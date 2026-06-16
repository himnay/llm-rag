package com.org.enrichment;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metadata-enrichment configuration ({@code app.enrichment.*}). Disabled by default because each
 * enricher makes an LLM call per chunk (cost + latency).
 */
@Data
@ConfigurationProperties(prefix = "app.enrichment")
public class EnrichmentProperties {

    private boolean enabled = false;
    private boolean keywords = true;
    private boolean summary = false;
    private int keywordCount = 5;
}
