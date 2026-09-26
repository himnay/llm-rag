package com.org.cache;

import com.org.common.Resilience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk-level content-hash dedup, backed by Redis, for documents <b>without</b> a stable identity.
 * Sources that have one (PDF#…, WIKI#…, DB#…) are diffed against the chunks Mongo stores for that
 * identity instead (see {@code KnowledgeLifecycleService}), which stays correct across deletes —
 * this cache only forgets a hash when its TTL runs out or {@link #clear()} is called.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkDedupService {

    private final StringRedisTemplate redisTemplate;
    private final ChunkDedupProperties properties;

    /** Hashes of. */
    public static String hashOf(String content) {
        return DigestUtils.sha256Hex(content);
    }

    /**
     * Atomically marks {@code contentHash} as seen. Returns {@code true} if it was new (and is now
     * stored), {@code false} if it already existed (a duplicate).
     */
    public boolean markIfNew(String contentHash) {
        if (!properties.isEnabled()) {
            return true;
        }
        String key = properties.getKeyPrefix() + contentHash;
        Boolean wasAbsent = Resilience.withRetry("redis chunk dedup", 3, 100L, () ->
                redisTemplate.opsForValue().setIfAbsent(key, "1", properties.getTtl()));
        return Boolean.TRUE.equals(wasAbsent);
    }

    /**
     * Hashes {@code content} and checks/marks it in one call.
     */
    public boolean isNewContent(String content) {
        return markIfNew(hashOf(content));
    }

    /**
     * Forgets every recorded hash (SCAN + batched DEL on the key prefix, never {@code KEYS}).
     * Called when the whole index is wiped, so previously seen content is stored again.
     */
    public void clear() {
        if (!properties.isEnabled()) {
            return;
        }
        ScanOptions options = ScanOptions.scanOptions().match(properties.getKeyPrefix() + "*").count(1000).build();
        List<String> batch = new ArrayList<>(1000);
        long removed = 0;
        try (Cursor<String> keys = redisTemplate.scan(options)) {
            while (keys.hasNext()) {
                batch.add(keys.next());
                if (batch.size() == 1000) {
                    removed += deleteKeys(batch);
                }
            }
        }
        removed += deleteKeys(batch);
        log.info("Cleared {} chunk-dedup hash(es)", removed);
    }

    private long deleteKeys(List<String> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.delete(List.copyOf(batch));
        batch.clear();
        return deleted == null ? 0 : deleted;
    }
}
