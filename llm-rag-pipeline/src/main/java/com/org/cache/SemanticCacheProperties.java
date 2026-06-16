package com.org.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.cache.semantic")
public class SemanticCacheProperties {

    /**
     * Cache query→answer pairs and return the cached answer when a new query is semantically
     * close enough (cosine similarity >= similarityThreshold). Big latency + cost win for
     * FAQ-style traffic.
     */
    private boolean enabled = false;

    /**
     * Maximum number of cached (query, answer) pairs.
     */
    private int maxSize = 500;

    /**
     * How long a cached answer remains valid.
     */
    private Duration ttl = Duration.ofMinutes(30);

    /**
     * Minimum cosine similarity between the incoming query vector and a cached query vector
     * required to serve the cached answer. Higher = stricter.
     */
    private double similarityThreshold = 0.95;
}
