package com.org.llm.dto;

import java.time.Instant;
import java.util.List;

public record RagResponse(
        String question,
        String answer,
        String graphContext,
        List<String> relevantEntities,
        long processingTimeMs,
        Instant timestamp
) {
    public static RagResponse of(String question, String answer,
                                  String graphContext, List<String> relevantEntities,
                                  long processingTimeMs) {
        return new RagResponse(question, answer, graphContext,
                relevantEntities, processingTimeMs, Instant.now());
    }
}
