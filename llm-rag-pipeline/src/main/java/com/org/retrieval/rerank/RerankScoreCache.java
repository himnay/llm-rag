package com.org.retrieval.rerank;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small, dependency-free TTL + LRU cache for rerank scores, keyed by
 * {@code strategy | query | sha256(chunk content)}. Re-running the same query re-pays the
 * cross-encoder vendor or the LLM for identical candidates — caching the per-chunk score (not the
 * list) lets {@link RerankingPostProcessor} reconstruct the ordering for free on a full hit.
 * Synchronized access is fine at retrieval QPS; swap in Caffeine if this ever becomes hot.
 */
final class RerankScoreCache {

    private record CachedScore(double score, long expiresAtMillis) {
    }

    private final Map<String, CachedScore> entries;
    private final long ttlMillis;

    RerankScoreCache(int maxSize, Duration ttl) {
        this.ttlMillis = ttl.toMillis();
        this.entries = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedScore> eldest) {
                return size() > maxSize;
            }
        };
    }

    /** The cached score, or {@code null} on miss/expiry. */
    synchronized Double get(String key) {
        CachedScore entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiresAtMillis()) {
            entries.remove(key);
            return null;
        }
        return entry.score();
    }

    synchronized void put(String key, double score) {
        entries.put(key, new CachedScore(score, System.currentTimeMillis() + ttlMillis));
    }

    static String key(RerankStrategy strategy, String query, String content) {
        return strategy + "|" + query + "|" + sha256(content);
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
