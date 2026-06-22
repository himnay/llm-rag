package com.org.exception;

/**
 * Thrown when the rate limiter's SHA-256 digest cannot be initialized. Indicates a broken JVM
 * security configuration rather than a request-time failure, so it is not REST-mapped.
 */
public class RateLimiterInitializationException extends RuntimeException {

    public RateLimiterInitializationException(String message) {
        super(message);
    }

    public RateLimiterInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public RateLimiterInitializationException(Throwable cause) {
        super(cause);
    }
}
