package com.org.vectorstore;

import com.org.chunking.model.Chunk;
import com.org.common.Resilience;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Writes chunks to the vector store. For large ingests the writes are partitioned into batches and
 * embedded + persisted <b>concurrently</b> on a shared, bounded executor to scale throughput, since
 * each {@code add} call embeds its batch (a network round-trip to the embedding model). Each batch
 * write is retried on transient failure.
 */
@Slf4j
@Service
public class ChunkVectorStoreService {

    private final VectorStore vectorStore;
    private final ExecutorService writeExecutor;

    @Value("${app.vectorstore.write.batch-size:50}")
    private int batchSize;

    @Value("${app.vectorstore.write.concurrency:4}")
    private int concurrency;

    public ChunkVectorStoreService(VectorStore vectorStore, ExecutorService vectorStoreWriteExecutor) {
        this.vectorStore = vectorStore;
        this.writeExecutor = vectorStoreWriteExecutor;
    }

    public void store(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        List<Document> documents = chunks.stream().map(chunk -> {
            Map<String, Object> metadata = new HashMap<>(chunk.metadata());
            metadata.put("source", chunk.source());
            metadata.put("chunkIndex", chunk.chunkIndex());
            return new Document(chunk.content(), metadata);
        }).collect(Collectors.toList());

        // Small input → single synchronous write.
        if (documents.size() <= batchSize || concurrency <= 1) {
            add(documents);
            return;
        }

        List<List<Document>> batches = partition(documents, batchSize);
        CompletableFuture<?>[] futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> add(batch), writeExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(futures).join(); // propagates the first failure
        log.info("Stored {} documents in {} parallel batches", documents.size(), batches.size());
    }

    /** A single batch write, retried on transient embedding/OpenSearch failures. */
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

    private static List<List<Document>> partition(List<Document> documents, int size) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < documents.size(); i += size) {
            batches.add(documents.subList(i, Math.min(i + size, documents.size())));
        }
        return batches;
    }
}
