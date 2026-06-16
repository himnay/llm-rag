package com.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record GenerateRequest(
        @NotBlank String query,
        @Positive Integer topK,
        /** Optional conversation ID for multi-turn chat. Omit for single-turn (default). */
        String conversationId) {
}
