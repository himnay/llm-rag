package com.org.llm.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Consistent error payload returned by {@link GlobalExceptionHandler}.
 * Matches the platform-wide ApiError convention:
 * {@code {"status":..., "error":"...", "message":"...", "timestamp":"...", "path":"..."}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        int status,
        String error,
        String message,
        Instant timestamp,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(status, error, message, Instant.now(), path, null);
    }

    public static ApiError of(int status, String error, String message, String path,
                              Map<String, String> fieldErrors) {
        return new ApiError(status, error, message, Instant.now(), path, fieldErrors);
    }
}
