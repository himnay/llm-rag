package com.org.cache;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.cache.chunk-dedup")
public class ChunkDedupProperties {

    /**
     * Skip writing a chunk whose content hash is already present in Redis.
     */
    private boolean enabled = true;

    /**
     * Redis key prefix for chunk content-hash entries.
     */
    private String keyPrefix = "chunkhash:";

    /**
     * How long a content hash is remembered. A chunk re-ingested after this window is re-embedded
     * once and the TTL restarts — a deliberate trade-off favoring bounded Redis memory over
     * permanent dedup state (dedup keys aren't cleared on source delete).
     */
    private Duration ttl = Duration.ofDays(30);
}
