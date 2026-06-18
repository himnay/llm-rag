package com.rag.vectorless.dto;

import java.util.List;

public record ChatResponse(String answer, List<Citation> citations, Boolean faithful) {
}
