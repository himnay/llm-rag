package com.org.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Write-through LRU + TTL cache for embedding vectors. Wraps {@link EmbeddingModel} so that
 * identical text strings are not re-embedded on each ingestion pass or semantic-chunking call.
 * Particularly valuable for {@code SemanticChunkingStrategy}, which embeds every sentence in a
 * document and would otherwise make one API call per sentence every time the same corpus is
 * re-ingested.
 *
 * <p>Keyed by {@code SHA-256(text)}. Thread-safe: a ConcurrentHashMap guards the write path so
 * the embedding call is never made while holding a lock. A ThreadLocal MessageDigest avoids the
 * per-call registry lookup overhead on the hot path. Swap to Caffeine if QPS is a bottleneck.</p>
 */
@Slf4j
@Service
public class EmbeddingCacheService {

    /**
     * Reuse one MessageDigest instance per thread — MessageDigest is NOT thread-safe.
     */
    private static final ThreadLocal<MessageDigest> SHA256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    });
    private final EmbeddingModel embeddingModel;
    private final EmbeddingCacheProperties properties;
    private final long maxSize;
    /**
     * LRU map guarded by its own intrinsic lock (only for eviction; writes are ConcurrentHashMap).
     */
    private final Map<String, CachedVector> lruOrder;
    private final ConcurrentHashMap<String, CachedVector> cache = new ConcurrentHashMap<>();
    public EmbeddingCacheService(EmbeddingModel embeddingModel, EmbeddingCacheProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.maxSize = properties.getMaxSize();
        this.lruOrder = new LinkedHashMap<>(64, 0.75f, true);
    }

    private static String sha256(String text) {
        MessageDigest md = SHA256.get();
        md.reset();
        return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Embed a single text string, returning the cached vector if available.
     */
    public float[] embed(String text) {
        if (!properties.isEnabled()) {
            return embeddingModel.embed(text);
        }
        String key = sha256(text);
        CachedVector cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAtMillis()) {
            return cached.vector();
        }
        // Embed outside any lock — network I/O must never block cache readers.
        float[] vector = embeddingModel.embed(text);
        long expiry = System.currentTimeMillis() + properties.getTtl().toMillis();
        CachedVector cv = new CachedVector(vector, expiry);
        cache.put(key, cv);
        evictIfNecessary(key);
        return vector;
    }

    /**
     * Embed a list of texts, hitting the cache per-item to avoid redundant API calls.
     */
    public List<float[]> embed(List<String> texts) {
        if (!properties.isEnabled()) {
            return embeddingModel.embed(texts);
        }
        long now = System.currentTimeMillis();
        List<String> uncachedTexts = new ArrayList<>();
        List<Integer> uncachedIdx = new ArrayList<>();
        List<float[]> result = new ArrayList<>(Collections.nCopies(texts.size(), null));

        for (int i = 0; i < texts.size(); i++) {
            CachedVector cv = cache.get(sha256(texts.get(i)));
            if (cv != null && now < cv.expiresAtMillis()) {
                result.set(i, cv.vector());
            } else {
                uncachedTexts.add(texts.get(i));
                uncachedIdx.add(i);
            }
        }

        if (!uncachedTexts.isEmpty()) {
            List<float[]> fetched = embeddingModel.embed(uncachedTexts);
            long expiry = System.currentTimeMillis() + properties.getTtl().toMillis();
            for (int j = 0; j < uncachedTexts.size(); j++) {
                float[] v = fetched.get(j);
                result.set(uncachedIdx.get(j), v);
                String key = sha256(uncachedTexts.get(j));
                cache.put(key, new CachedVector(v, expiry));
                evictIfNecessary(key);
            }
            log.debug("EmbeddingCache: {} hit(s), {} miss(es)", texts.size() - uncachedTexts.size(), uncachedTexts.size());
        }
        return result;
    }

    /**
     * Evict LRU entry when the cache exceeds maxSize.
     */
    private void evictIfNecessary(String recentKey) {
        synchronized (lruOrder) {
            lruOrder.put(recentKey, null);
            if (lruOrder.size() > maxSize) {
                String eldest = lruOrder.keySet().iterator().next();
                lruOrder.remove(eldest);
                cache.remove(eldest);
            }
        }
    }

    private record CachedVector(float[] vector, long expiresAtMillis) {
    }
}
