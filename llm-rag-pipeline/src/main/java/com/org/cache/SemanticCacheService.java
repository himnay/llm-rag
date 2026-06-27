package com.org.cache;

import com.org.common.VectorMath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Semantic answer cache: stores {@code (queryVector, answer)} pairs and, on a new query, returns a
 * cached answer when the new query's embedding is within {@code similarityThreshold} cosine
 * similarity of a stored query. This avoids a full retrieve→generate round-trip for queries that
 * are semantically equivalent to one already answered — a major latency and cost win for FAQ-style
 * traffic.
 *
 * <p>Backed by an in-memory list (O(n) scan); suitable for small caches (≤500 entries). Swap to
 * a proper ANN index if the cache grows large.</p>
 */
@Slf4j
@Service
public class SemanticCacheService {

    private final EmbeddingCacheService embeddingCacheService;
    private final SemanticCacheProperties properties;
    private final List<CacheEntry> entries = new ArrayList<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Wires the embedding service used to vectorize incoming queries.
     */
    public SemanticCacheService(EmbeddingCacheService embeddingCacheService,
                                SemanticCacheProperties properties) {
        this.embeddingCacheService = embeddingCacheService;
        this.properties = properties;
    }

    /**
     * Look up a cached answer for the given query. Returns {@link Optional#empty()} on a miss or
     * when semantic caching is disabled.
     */
    public Optional<String> get(String query) {
        if (!properties.isEnabled()) return Optional.empty();

        float[] queryVector = embeddingCacheService.embed(query);
        double threshold = properties.getSimilarityThreshold();
        long now = System.currentTimeMillis();

        double bestSim = -1;
        String bestAnswer = null;

        lock.readLock().lock();
        try {
            for (CacheEntry entry : entries) {
                double sim = cosine(queryVector, entry.queryVector());
                if (sim >= threshold && sim > bestSim) {
                    bestSim = sim;
                    bestAnswer = entry.answer();
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        if (bestAnswer != null) {
            log.debug("SemanticCache hit | similarity={} | query='{}'", String.format("%.4f", bestSim), query);
            return Optional.of(bestAnswer);
        }
        return Optional.empty();
    }

    /**
     * Store a query→answer pair. Evicts the oldest entry if the cache is full.
     */
    public void put(String query, String answer) {
        if (!properties.isEnabled()) return;
        if (answer == null || answer.isBlank()) return;

        float[] queryVector = embeddingCacheService.embed(query);
        long expiry = System.currentTimeMillis() + properties.getTtl().toMillis();
        lock.writeLock().lock();
        try {
            if (entries.size() >= properties.getMaxSize()) {
                entries.remove(0);
            }
            entries.add(new CacheEntry(queryVector, answer, expiry));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Scheduled(fixedRate = 30_000)
    void evictExpiredEntries() {
        long now = System.currentTimeMillis();
        lock.writeLock().lock();
        try {
            entries.removeIf(e -> now >= e.expiresAtMillis());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private static double cosine(float[] a, float[] b) {
        return VectorMath.cosine(a, b);
    }

    private record CacheEntry(float[] queryVector, String answer, long expiresAtMillis) {
    }
}
