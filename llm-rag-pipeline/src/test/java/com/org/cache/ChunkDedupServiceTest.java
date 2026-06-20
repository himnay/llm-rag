package com.org.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChunkDedupServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    private ChunkDedupService newService(boolean enabled) {
        ChunkDedupProperties properties = new ChunkDedupProperties();
        properties.setEnabled(enabled);
        properties.setKeyPrefix("chunkhash:");
        properties.setTtl(Duration.ofDays(30));
        return new ChunkDedupService(redisTemplate, properties);
    }

    @Test
    @DisplayName("New content hash is marked in Redis with the configured TTL and reported as new")
    void newContentIsMarkedAndReportedNew() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        boolean result = newService(true).isNewContent("hello world");

        assertThat(result).isTrue();
        verify(valueOps).setIfAbsent(eq("chunkhash:" + ChunkDedupService.hashOf("hello world")), eq("1"), eq(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("Existing content hash (SETNX returns false) is reported as a duplicate")
    void existingContentIsReportedAsDuplicate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        boolean result = newService(true).isNewContent("hello world");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Disabled dedup always reports content as new without touching Redis")
    void disabledDedupSkipsRedis() {
        boolean result = newService(false).isNewContent("hello world");

        assertThat(result).isTrue();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("hashOf is a pure SHA-256 hex digest of the content")
    void hashOfIsShaHex() {
        assertThat(ChunkDedupService.hashOf("abc")).hasSize(64).matches("[0-9a-f]+");
        assertThat(ChunkDedupService.hashOf("abc")).isEqualTo(ChunkDedupService.hashOf("abc"));
    }
}
