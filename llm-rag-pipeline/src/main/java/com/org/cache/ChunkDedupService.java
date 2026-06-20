package com.org.cache;

import com.org.common.Resilience;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Chunk-level content-hash dedup, backed by Redis. Replaces the old whole-document
 * (identity-level) hash check against Postgres — dedup now happens per chunk, after chunking,
 * so a partial document change only re-embeds the chunks that actually changed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkDedupService {

    private final StringRedisTemplate redisTemplate;
    private final ChunkDedupProperties properties;

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
}
