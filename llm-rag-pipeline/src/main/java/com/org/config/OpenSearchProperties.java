package com.org.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.ai.vectorstore.opensearch")
public class OpenSearchProperties {

    private String indexName = "nexacorp_index";
    private int dimensions = 0;
}
