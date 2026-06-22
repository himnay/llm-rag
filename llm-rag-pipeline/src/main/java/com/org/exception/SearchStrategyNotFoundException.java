package com.org.exception;

/**
 * Thrown when no {@code SearchStrategy} is registered for the resolved search mode.
 */
public class SearchStrategyNotFoundException extends RuntimeException {

    public SearchStrategyNotFoundException(String message) {
        super(message);
    }

    public SearchStrategyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
