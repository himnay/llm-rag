package com.org.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.cache.embedding")
public class EmbeddingCacheProperties {

    /**
     * Cache text→vector results to avoid re-embedding identical text (e.g. during semantic chunking).
     */
    private boolean enabled = true;

    /**
     * Maximum number of (text, vector) pairs to hold in memory.
     */
    private int maxSize = 5000;

    /**
     * How long a cached vector remains valid.
     */
    private Duration ttl = Duration.ofHours(24);
}
