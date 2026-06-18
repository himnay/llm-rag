package com.org.llm.dto;

import java.time.Instant;
import java.util.List;

public record RagResponse(
        String question,
        String answer,
        String graphContext,
        List<String> relevantEntities,
        List<Citation> citations,
        Boolean groundedness,
        long processingTimeMs,
        Instant timestamp
) {
    /**
     * Builds a response with no citations or groundedness verdict, timestamped now.
     */
    public static RagResponse of(String question, String answer,
                                 String graphContext, List<String> relevantEntities,
                                 long processingTimeMs) {
        return new RagResponse(question, answer, graphContext,
                relevantEntities, List.of(), null, processingTimeMs, Instant.now());
    }

    /**
     * Builds a response with citations and an optional groundedness verdict, timestamped now.
     */
    public static RagResponse of(String question, String answer,
                                 String graphContext, List<String> relevantEntities,
                                 List<Citation> citations, Boolean groundedness,
                                 long processingTimeMs) {
        return new RagResponse(question, answer, graphContext,
                relevantEntities, citations, groundedness, processingTimeMs, Instant.now());
    }
}
