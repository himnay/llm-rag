package com.org.vectorstore;

import com.org.chunking.ChunkIdGenerator;
import com.org.chunking.model.Chunk;
import com.org.common.Resilience;
import com.org.mongo.ChunkDocument;
import com.org.mongo.ChunkDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    private final VectorStore vectorStore;
    @Qualifier("vectorStoreWriteExecutor")
    private final ExecutorService writeExecutor;
    private final VectorStoreWriteProperties props;
    private final ChunkDocumentRepository chunkDocumentRepository;

    private static List<List<Document>> partition(List<Document> documents, int size) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += size) {
            batches.add(documents.subList(i, Math.min(i + size, documents.size())));
        }
        return batches;
    }

    public void store(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        String modelId = props.getEmbeddingModelId();
        int dimension = props.getEmbeddingDimension();
        List<Document> documents = new ArrayList<>(chunks.size());
        List<ChunkDocument> mongoDocs = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            Map<String, Object> metadata = new HashMap<>(chunk.metadata());
            metadata.put("source", chunk.source());
            metadata.put("chunkIndex", chunk.chunkIndex());
            metadata.put("embeddingModel", modelId);
            metadata.put("embeddingDimension", dimension);
            metadata.put("chunkId", ChunkIdGenerator.idFor(chunk));
            documents.add(new Document(chunk.content(), metadata));
            mongoDocs.add(toMongoDocument(chunk, metadata));
        }

        Resilience.withRetry("mongo chunk upsert", 3, 200L, () -> chunkDocumentRepository.upsertAll(mongoDocs));

        if (log.isDebugEnabled()) {
            documents.forEach(d -> log.debug("Persisting chunk — source='{}' chunkIndex={} embeddingModel='{}' metadataKeys={}",
                    d.getMetadata().get("source"), d.getMetadata().get("chunkIndex"),
                    d.getMetadata().get("embeddingModel"), d.getMetadata().keySet()));
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

    /**
     * A single batch write, retried on transient embedding/OpenSearch failures.
     */
    private void add(List<Document> batch) {
        Resilience.withRetry("vector store add", 3, 200L, () -> vectorStore.add(batch));
    }

    public void deleteAll() {
        // Every chunk has chunkIndex >= 0, so this matches all stored documents.
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        Filter.Expression filter = filterBuilder.gte("chunkIndex", 0).build();
        vectorStore.delete(filter);
    }

    public void deleteByIdentity(String identity) {
        FilterExpressionBuilder filterBuilder = new FilterExpressionBuilder();
        vectorStore.delete(filterBuilder.eq("identity", identity).build());
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
