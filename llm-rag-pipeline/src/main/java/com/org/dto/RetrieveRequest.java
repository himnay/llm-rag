package com.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request body for {@code POST /api/retrieve}.
 *
 * @param query the search query (required)
 * @param topK  optional number of chunks to retrieve; falls back to the configured default
 */
public record RetrieveRequest(
        @NotBlank(message = "query must not be blank") String query,
        @Positive(message = "topK must be positive when provided") Integer topK) {
}
