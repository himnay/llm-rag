package com.org.cache;

import com.org.common.VectorMath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

        synchronized (entries) {
            entries.removeIf(e -> now >= e.expiresAtMillis());
            for (CacheEntry entry : entries) {
                double sim = cosine(queryVector, entry.queryVector());
                if (sim >= threshold && sim > bestSim) {
                    bestSim = sim;
                    bestAnswer = entry.answer();
                }
            }
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

        float[] queryVector = embeddingCacheService.embed(query);
        long expiry = System.currentTimeMillis() + properties.getTtl().toMillis();
        synchronized (entries) {
            if (entries.size() >= properties.getMaxSize()) {
                entries.remove(0);
            }
            entries.add(new CacheEntry(queryVector, answer, expiry));
        }
    }

    private static double cosine(float[] a, float[] b) {
        return VectorMath.cosine(a, b);
    }

    private record CacheEntry(float[] queryVector, String answer, long expiresAtMillis) {
    }
}
