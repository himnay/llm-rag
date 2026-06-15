package com.org.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Write-through LRU + TTL cache for embedding vectors. Wraps {@link EmbeddingModel} so that
 * identical text strings are not re-embedded on each ingestion pass or semantic-chunking call.
 * Particularly valuable for {@code SemanticChunkingStrategy}, which embeds every sentence in a
 * document and would otherwise make one API call per sentence every time the same corpus is
 * re-ingested.
 *
 * <p>Keyed by {@code SHA-256(text)}. Thread-safe via synchronization — swap to Caffeine if QPS
 * makes this a bottleneck.</p>
 */
@Slf4j
@Service
public class EmbeddingCacheService {

    private record CachedVector(float[] vector, long expiresAtMillis) {}

    private final EmbeddingModel embeddingModel;
    private final EmbeddingCacheProperties properties;
    private final Map<String, CachedVector> cache;

    public EmbeddingCacheService(EmbeddingModel embeddingModel, EmbeddingCacheProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        long maxSize = properties.getMaxSize();
        this.cache = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedVector> eldest) {
                return size() > maxSize;
            }
        };
    }

    /** Embed a single text string, returning the cached vector if available. */
    public float[] embed(String text) {
        if (!properties.isEnabled()) {
            return embeddingModel.embed(text);
        }
        String key = sha256(text);
        synchronized (cache) {
            CachedVector cached = cache.get(key);
            if (cached != null && System.currentTimeMillis() < cached.expiresAtMillis()) {
                return cached.vector();
            }
        }
        float[] vector = embeddingModel.embed(text);
        synchronized (cache) {
            cache.put(key, new CachedVector(vector, System.currentTimeMillis() + properties.getTtl().toMillis()));
        }
        return vector;
    }

    /** Embed a list of texts, hitting the cache per-item to avoid redundant API calls. */
    public List<float[]> embed(List<String> texts) {
        if (!properties.isEnabled()) {
            return embeddingModel.embed(texts);
        }
        List<String> uncached = new ArrayList<>();
        List<Integer> uncachedIdx = new ArrayList<>();
        List<float[]> result = new ArrayList<>(texts.size());

        for (int i = 0; i < texts.size(); i++) {
            String key = sha256(texts.get(i));
            synchronized (cache) {
                CachedVector cv = cache.get(key);
                if (cv != null && System.currentTimeMillis() < cv.expiresAtMillis()) {
                    result.add(cv.vector());
                    continue;
                }
            }
            result.add(null);
            uncached.add(texts.get(i));
            uncachedIdx.add(i);
        }

        if (!uncached.isEmpty()) {
            List<float[]> fetched = embeddingModel.embed(uncached);
            long expiry = System.currentTimeMillis() + properties.getTtl().toMillis();
            for (int j = 0; j < uncached.size(); j++) {
                float[] v = fetched.get(j);
                result.set(uncachedIdx.get(j), v);
                synchronized (cache) {
                    cache.put(sha256(uncached.get(j)), new CachedVector(v, expiry));
                }
            }
            log.debug("EmbeddingCache: {} hit(s), {} miss(es)", texts.size() - uncached.size(), uncached.size());
        }
        return result;
    }

    private static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
