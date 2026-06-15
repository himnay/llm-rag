package com.org.dto;

import com.org.retrieval.model.Citation;

import java.util.List;

public record GenerateResponse(
        String answer,
        List<Citation> citations,
        Boolean faithful,
        boolean fromSemanticCache) {
}
