package com.org.vectorstore;

import com.org.chunking.ChunkIdGenerator;
import com.org.chunking.model.Chunk;
import com.org.common.Resilience;
import com.org.config.OpenSearchProperties;
import com.org.mongo.ChunkDocument;
import com.org.mongo.ChunkDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Conflicts;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Dual-writes chunks: full text + descriptive metadata to MongoDB (keyed by chunkId, the system of
 * record for chunk content) and the embedded vector + filter fields + chunkId to the vector store
 * (OpenSearch). Mongo is written first so a Mongo failure aborts before any embedding spend, and so
 * OpenSearch never ends up with vectors pointing at chunkIds that have no hydratable text.
 *
 * <p>For large ingests the OpenSearch writes are partitioned into batches and embedded + persisted
 * <b>concurrently</b> on a shared, bounded executor to scale throughput, since each {@code add} call
 * embeds its batch (a network round-trip to the embedding model). Each batch write is retried on
 * transient failure.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkVectorStoreService {


    @Qualifier("vectorStoreWriteExecutor")
    private final ExecutorService writeExecutor;

    private final VectorStore vectorStore;
    private final VectorStoreWriteProperties props;
    private final ChunkDocumentRepository chunkDocumentRepository;
    private final OpenSearchClient openSearchClient;
    private final OpenSearchProperties openSearchProperties;

    /** Handles store. */
    public void store(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        String modelId = props.getEmbeddingModelId();
        int dimension = props.getEmbeddingDimension();
        List<Document> documents = new ArrayList<>(chunks.size());
        List<ChunkDocument> mongoDocs = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            String chunkId = ChunkIdGenerator.idFor(chunk);

            Map<String, Object> mongoMetadata = new HashMap<>(chunk.metadata());
            mongoMetadata.put("source", chunk.source());
            mongoMetadata.put("chunkIndex", chunk.chunkIndex());
            mongoMetadata.put("embeddingModel", modelId);
            mongoMetadata.put("embeddingDimension", dimension);
            mongoMetadata.put("chunkId", chunkId);
            mongoDocs.add(toMongoDocument(chunk, mongoMetadata));

            // OpenSearch only needs the fields used for filtering/joins; everything else
            // (descriptive chunk metadata) lives in Mongo so updating it never forces a re-embed.
            Map<String, Object> searchMetadata = new HashMap<>();
            searchMetadata.put("chunkId", chunkId);
            searchMetadata.put("chunkIndex", chunk.chunkIndex());
            searchMetadata.put("source", chunk.source());
            Object identity = chunk.metadata().get("identity");
            if (identity != null) {
                searchMetadata.put("identity", identity);
            }
            // _id = chunkId: re-adding a chunk overwrites its previous vector instead of duplicating it.
            documents.add(Document.builder().id(chunkId).text(chunk.content()).metadata(searchMetadata).build());
        }

        Resilience.withRetry("mongo chunk upsert", 3, 200L, () -> chunkDocumentRepository.upsertAll(mongoDocs));

        if (log.isDebugEnabled()) {
            log.debug("Persisting {} chunk(s) — embeddingModel='{}' embeddingDimension={}",
                    documents.size(), modelId, dimension);
        }

        // Small input → single synchronous write.
        if (documents.size() <= props.getBatchSize() || props.getConcurrency() <= 1) {
            add(documents);
            return;
        }

        List<List<Document>> batches = partition(documents, props.getBatchSize());
        CompletableFuture<?>[] futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> add(batch), writeExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join(); // propagates the first failure
        log.info("Stored {} documents in {} parallel batches", documents.size(), batches.size());
    }

    /** Deletes every stored vector. */
    public void deleteAll() {
        deleteByQuery("delete all", Query.of(q -> q.matchAll(m -> m)));
    }

    /**
     * Deletes every vector of one source. Matches the identity exactly (keyword), unlike a
     * query_string phrase on the analysed text field, which also hits identities that merely
     * contain the phrase ("WIKI#Leave Policy" would take "WIKI#Leave Policy Archive" with it).
     */
    public void deleteByIdentity(String identity) {
        deleteByQuery("delete identity", exactMetadata("identity", List.of(identity)));
    }

    /**
     * Cleans up after an incremental re-ingest: removes the {@code staleChunkIds} (chunks the
     * source no longer produces) and any leftover copy of a {@code rewrittenChunkIds} chunk that
     * was indexed under a random id before ids became deterministic. The fresh copies, stored
     * under {@code _id = chunkId}, are excluded, so this is safe to run after {@link #store}.
     */
    public void deleteReplaced(Collection<String> staleChunkIds, Collection<String> rewrittenChunkIds) {
        if (!staleChunkIds.isEmpty()) {
            deleteByQuery("delete stale chunks", Query.of(q -> q.bool(b -> b
                    .should(Query.of(s -> s.ids(i -> i.values(List.copyOf(staleChunkIds)))))
                    .should(exactMetadata("chunkId", staleChunkIds))
                    .minimumShouldMatch("1"))));
        }
        if (!rewrittenChunkIds.isEmpty()) {
            deleteByQuery("delete legacy duplicates", Query.of(q -> q.bool(b -> b
                    .must(exactMetadata("chunkId", rewrittenChunkIds))
                    .mustNot(Query.of(s -> s.ids(i -> i.values(List.copyOf(rewrittenChunkIds))))))));
        }
    }

    /**
     * Term match on a metadata field under both mappings it can have: the default dynamic
     * mapping (text + {@code .keyword} sub-field) and an explicit {@code keyword} mapping.
     */
    private static Query exactMetadata(String field, Collection<String> values) {
        List<FieldValue> terms = values.stream().map(FieldValue::of).toList();
        return Query.of(q -> q.bool(b -> b
                .should(Query.of(s -> s.terms(t -> t.field("metadata." + field + ".keyword").terms(v -> v.value(terms)))))
                .should(Query.of(s -> s.terms(t -> t.field("metadata." + field).terms(v -> v.value(terms)))))
                .minimumShouldMatch("1")));
    }

    /**
     * {@code _delete_by_query} with {@code conflicts=proceed} and {@code refresh=true}. Spring AI's
     * filter delete aborts on the first version conflict, and a conflict is exactly what happens
     * when a document it matched was already deleted but the index hadn't refreshed yet (e.g. a
     * delete-all right after a per-source delete) — the end state we want, reported as HTTP 409.
     * Refreshing makes the deletion visible to the next search/delete straight away.
     */
    private void deleteByQuery(String operation, Query query) {
        String index = openSearchProperties.getIndexName();
        DeleteByQueryResponse response = Resilience.withRetry("vector store " + operation, 3, 200L, () -> {
            try {
                return openSearchClient.deleteByQuery(d -> d
                        .index(index)
                        .query(query)
                        .conflicts(Conflicts.Proceed)
                        .refresh(Refresh.True));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        if (!response.failures().isEmpty()) {
            throw new IllegalStateException("OpenSearch " + operation + " failed for "
                    + response.failures().size() + " document(s): " + response.failures().getFirst());
        }
        log.debug("OpenSearch {}: deleted={} versionConflicts={}", operation, response.deleted(), response.versionConflicts());
    }

    private static List<List<Document>> partition(List<Document> documents, int size) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += size) {
            batches.add(documents.subList(i, Math.min(i + size, documents.size())));
        }
        return batches;
    }

    /**
     * A single batch write, retried on transient embedding/OpenSearch failures.
     */
    private void add(List<Document> batch) {
        Resilience.withRetry("vector store add", 3, 200L, () -> vectorStore.add(batch));
    }

    private static ChunkDocument toMongoDocument(Chunk chunk, Map<String, Object> metadata) {
        ChunkDocument doc = new ChunkDocument();
        doc.setChunkId((String) metadata.get("chunkId"));
        doc.setIdentity(Objects.toString(metadata.get("identity"), null));
        doc.setSource(chunk.source());
        doc.setChunkIndex(chunk.chunkIndex());
        doc.setContent(chunk.content());
        doc.setMetadata(metadata);
        return doc;
    }
}
