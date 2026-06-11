package com.org.enrichment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Metadata-enrichment configuration ({@code app.enrichment.*}). Disabled by default because each
 * enricher makes an LLM call per chunk (cost + latency).
 */
@ConfigurationProperties(prefix = "app.enrichment")
public class EnrichmentProperties {

    private boolean enabled = false;
    private boolean keywords = true;
    private boolean summary = false;
    private int keywordCount = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isKeywords() { return keywords; }
    public void setKeywords(boolean keywords) { this.keywords = keywords; }
    public boolean isSummary() { return summary; }
    public void setSummary(boolean summary) { this.summary = summary; }
    public int getKeywordCount() { return keywordCount; }
    public void setKeywordCount(int keywordCount) { this.keywordCount = keywordCount; }
}
