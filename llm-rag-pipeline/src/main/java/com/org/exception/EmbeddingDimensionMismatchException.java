package com.org.exception;

/**
 * Thrown at startup when the configured OpenSearch vector dimension doesn't match the embedding
 * model's actual output dimension. A mismatch silently breaks kNN search, so this is fail-fast.
 */
public class EmbeddingDimensionMismatchException extends RuntimeException {

    public EmbeddingDimensionMismatchException(String message) {
        super(message);
    }

    public EmbeddingDimensionMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
