package com.rag.vectorless.exception;

/**
 * Thrown when a PageIndex document fails to process, or doesn't finish processing within the
 * polling window.
 */
public class PageIndexProcessingException extends RuntimeException {

    public PageIndexProcessingException(String message) {
        super(message);
    }

    public PageIndexProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
