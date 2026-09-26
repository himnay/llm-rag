package com.org.lifecycle;

import com.org.cache.ChunkDedupService;
import com.org.chunking.ChunkIdGenerator;
import com.org.chunking.ChunkingOrchestrator;
import com.org.chunking.model.Chunk;
import com.org.enrichment.ChunkEnricher;
import com.org.ingestion.IngestionOrchestrator;
import com.org.ingestion.TextNormalizer;
import com.org.ingestion.model.IngestedDocument;
import com.org.lifecycle.event.IngestionCompletedEvent;
import com.org.lifecycle.event.VectorsStoredEvent;
import com.org.lifecycle.model.KnowledgeRequest;
import com.org.mongo.ChunkDocumentRepository;
import com.org.vectorstore.ChunkVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeLifecycleService {

    /**
     * Sentinel prefix for documents with no stable identity (never deduplicated).
     * Uses "anon-" prefix which cannot collide with real identities (PDF#..., WIKI#..., DB#...).
     */
    private static final String ANON = "anon-";

    private final AtomicBoolean ingestAllRunning = new AtomicBoolean(false);

    private final ChunkVectorStoreService vectorStoreService;
    private final IngestionOrchestrator ingestionOrchestrator;
    private final ChunkingOrchestrator chunkingOrchestrator;
    private final TextNormalizer textNormalizer;
    private final ChunkEnricher chunkEnricher;
    private final IngestionLogRepository ingestionLog;
    private final ChunkDocumentRepository chunkDocumentRepository;
    private final ChunkDedupService chunkDedupService;
    private final ApplicationEventPublisher eventPublisher;

    @Qualifier("ingestionExecutor")
    private final Executor ingestionExecutor;

    /**
     * Ingests the single source described by {@code request} through the clean → chunk → enrich
     * → store pipeline.
     */
    public void ingest(KnowledgeRequest request) throws IOException {
        process(ingestionOrchestrator.ingest(request), false);
    }

    /**
     * Wipes the vector store and re-ingests every known knowledge source from scratch.
     * Guard: rejects concurrent calls — only one ingest-all may run at a time.
     */
    public void ingestAll() throws IOException {
        if (!ingestAllRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("An ingest-all operation is already in progress. Try again after it completes.");
        }
        try {
            deleteAll();
            process(ingestionOrchestrator.ingestAll(), true);
        } finally {
            ingestAllRunning.set(false);
        }
    }

    /**
     * Ingests already-read documents (used by file upload + the inbox scheduler).
     */
    public void ingestDocuments(List<IngestedDocument> documents) {
        process(documents, false);
    }

    /**
     * Deletes the vectors and ingestion-log entry for the source described by {@code request}.
     */
    public void delete(KnowledgeRequest request) {
        String identity = KnowledgeIdentity.from(request);
        vectorStoreService.deleteByIdentity(identity);
        chunkDocumentRepository.deleteByIdentity(identity);
        ingestionLog.deleteByIdentity(identity);
    }

    /**
     * Deletes every vector and ingestion-log entry.
     */
    public void deleteAll() {
        vectorStoreService.deleteAll();
        chunkDocumentRepository.deleteAll();
        ingestionLog.deleteAll();
        chunkDedupService.clear(); // or identity-less sources would be skipped as "already present"
    }

    // ── pipeline: clean → (dedup) → chunk → store ───────────────────────────────

    /**
     * Cleans, deduplicates, chunks, enriches and stores documents grouped by identity.
     * Identity groups are processed in parallel on the ingestion executor.
     *
     * <p>Unless {@code force} is set, a source with a stable identity is diffed against the chunks
     * Mongo holds for it: unchanged chunks are left alone (no re-embedding), new or edited chunks
     * are stored — overwriting their previous vector, since {@code _id = chunkId} — and chunks the
     * source no longer produces are deleted only <em>after</em> the store succeeded, so a failed
     * write never leaves the source half-deleted.</p>
     */
    private void process(List<IngestedDocument> documents, boolean force) {
        Map<String, List<IngestedDocument>> byIdentity = groupByIdentity(documents);

        ConcurrentLinkedQueue<Chunk> chunkQueue = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> staleChunkIds = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<String> rewrittenChunkIds = new ConcurrentLinkedQueue<>();
        ConcurrentHashMap<String, Integer> countByIdentity = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = byIdentity.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(
                        () -> processGroup(entry.getKey(), entry.getValue(), force, chunkQueue,
                                staleChunkIds, rewrittenChunkIds, countByIdentity),
                        ingestionExecutor))
                .collect(Collectors.toList());

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Ingestion failed for one or more identity groups: {}", cause.getMessage(), cause);
            throw e;
        }

        List<Chunk> chunksToStore = new ArrayList<>(chunkQueue);
        if (chunksToStore.isEmpty()) {
            removeStale(staleChunkIds, rewrittenChunkIds);
            log.info("Nothing to store — all {} document(s) unchanged (chunk-level dedup)", documents.size());
            return;
        }
        chunksToStore = chunkEnricher.enrich(chunksToStore);
        log.info("Storing {} chunk(s) from {} document(s)", chunksToStore.size(), documents.size());
        log.debug("Chunk metadata snapshot before store: {}",
                chunksToStore.stream().map(c -> Map.of("source", c.source(), "chunkIndex", c.chunkIndex(),
                        "metadata", c.metadata())).collect(Collectors.toList()));
        vectorStoreService.store(chunksToStore);
        removeStale(staleChunkIds, rewrittenChunkIds);

        eventPublisher.publishEvent(new IngestionCompletedEvent(this, documents.size(), chunksToStore.size()));
        eventPublisher.publishEvent(new VectorsStoredEvent(this, chunksToStore.size(),
                countByIdentity.size() == 1 ? countByIdentity.keySet().iterator().next() : null));
    }

    private void processGroup(String key, List<IngestedDocument> group, boolean force,
                              ConcurrentLinkedQueue<Chunk> chunkQueue,
                              ConcurrentLinkedQueue<String> staleChunkIds,
                              ConcurrentLinkedQueue<String> rewrittenChunkIds,
                              ConcurrentHashMap<String, Integer> countByIdentity) {
        String identity = key.startsWith(ANON) ? null : key;

        List<Chunk> allChunks = new ArrayList<>();
        for (IngestedDocument document : group) {
            allChunks.addAll(chunkingOrchestrator.chunk(document));
        }

        List<Chunk> newChunks;
        if (identity == null) {
            // No stable identity to diff against: fall back to the content-hash cache. Under force
            // (ingest-all, index just wiped) every chunk is stored but still recorded in the cache.
            newChunks = allChunks.stream()
                    .filter(chunk -> chunkDedupService.isNewContent(chunk.content()) || force)
                    .collect(Collectors.toList());
        } else if (force) {
            newChunks = allChunks; // ingest-all: the index was just wiped
        } else {
            Map<String, String> stored = chunkDocumentRepository.contentHashesByIdentity(identity);
            Set<String> current = new HashSet<>();
            newChunks = new ArrayList<>();
            for (Chunk chunk : allChunks) {
                String chunkId = ChunkIdGenerator.idFor(chunk);
                current.add(chunkId);
                String storedHash = stored.get(chunkId);
                if (!ChunkDedupService.hashOf(chunk.content()).equals(storedHash)) {
                    newChunks.add(chunk);
                    if (storedHash != null) {
                        rewrittenChunkIds.add(chunkId);
                    }
                }
            }
            stored.keySet().stream().filter(chunkId -> !current.contains(chunkId)).forEach(staleChunkIds::add);
        }

        log.debug("Processing identity='{}' docCount={} chunked={} new={}",
                identity, group.size(), allChunks.size(), newChunks.size());

        if (newChunks.isEmpty()) {
            log.info("Skipping identity={} — all {} chunk(s) already present (content-hash match)",
                    identity, allChunks.size());
            return;
        }
        chunkQueue.addAll(newChunks);
        if (identity != null) {
            countByIdentity.put(identity, newChunks.size());
        }
    }

    /** Removes chunks a re-ingested source no longer produces (runs after the store succeeded). */
    private void removeStale(Collection<String> staleChunkIds, Collection<String> rewrittenChunkIds) {
        if (staleChunkIds.isEmpty() && rewrittenChunkIds.isEmpty()) {
            return;
        }
        vectorStoreService.deleteReplaced(List.copyOf(staleChunkIds), List.copyOf(rewrittenChunkIds));
        chunkDocumentRepository.deleteByIds(List.copyOf(staleChunkIds));
        log.info("Removed {} stale chunk(s) after re-ingest", staleChunkIds.size());
    }

    /**
     * Normalizes each document and groups by its {@code identity} metadata (order-preserving).
     */
    private Map<String, List<IngestedDocument>> groupByIdentity(List<IngestedDocument> documents) {
        Map<String, List<IngestedDocument>> byIdentity = new LinkedHashMap<>();
        int anon = 0;
        for (IngestedDocument document : documents) {
            IngestedDocument cleaned = textNormalizer.normalize(document);
            String identity = Objects.toString(cleaned.metadata().get("identity"), "");
            String key = identity.isEmpty() ? ANON + (anon++) : identity;
            byIdentity.computeIfAbsent(key, k -> new ArrayList<>()).add(cleaned);
        }
        return byIdentity;
    }
}
