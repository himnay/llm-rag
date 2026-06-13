package com.org.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.ai.vectorstore.opensearch")
public class OpenSearchProperties {

    private String indexName = "nexacorp_index";
    private int dimensions = 0;

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }
    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
}
