package com.org.llm.service;

/**
 * Thrown when the Anthropic API call fails (network, auth, rate-limit, or model error).
 */
public class LlmCallException extends RuntimeException {

    /**
     * Wraps the underlying SDK/network failure with a descriptive message.
     */
    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
