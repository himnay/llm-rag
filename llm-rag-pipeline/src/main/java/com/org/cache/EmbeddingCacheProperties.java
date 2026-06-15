package com.org.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache.embedding")
public class EmbeddingCacheProperties {

    /** Cache text→vector results to avoid re-embedding identical text (e.g. during semantic chunking). */
    private boolean enabled = true;

    /** Maximum number of (text, vector) pairs to hold in memory. */
    private int maxSize = 5000;

    /** How long a cached vector remains valid. */
    private Duration ttl = Duration.ofHours(24);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration ttl) { this.ttl = ttl; }
}
