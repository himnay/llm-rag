package com.org.lifecycle.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Request to ingest or delete a knowledge source.
 *
 * @param sourceType PDF, WIKI or DATABASE (required)
 * @param name       file name (PDF/WIKI) or table name (DATABASE) (required)
 */
@Builder
public record KnowledgeRequest(
        @NotNull(message = "sourceType is required (PDF, WIKI or DATABASE)") SourceType sourceType,
        @NotBlank(message = "name is required") String name) {
}
