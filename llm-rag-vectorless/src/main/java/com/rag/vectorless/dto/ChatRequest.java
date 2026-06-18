package com.rag.vectorless.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "question must not be blank")
        @Size(max = 4000, message = "question must not exceed 4000 characters")
        String question
) {
}
