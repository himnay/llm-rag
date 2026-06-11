package com.rag.vectorless.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        @DefaultValue("500") int chunkSize,
        @DefaultValue("100") int chunkOverlap,
        @DefaultValue("5") int topK
) {}
