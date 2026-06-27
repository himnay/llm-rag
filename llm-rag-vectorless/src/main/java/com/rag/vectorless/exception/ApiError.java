package com.rag.vectorless.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Consistent error payload returned by
 * {@link com.rag.vectorless.controller.GlobalExceptionHandler}.
 * Matches the platform-wide ApiError convention:
 * {@code {"status":..., "error":"...", "message":"...", "timestamp":"...", "path":"..."}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        String path
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, Instant.now(), path);
    }
}
