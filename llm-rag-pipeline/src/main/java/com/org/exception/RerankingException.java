package com.org.exception;

/**
 * Thrown when LLM-based reranking fails or is interrupted, or when the rerank score cache cannot
 * compute its content hash.
 */
public class RerankingException extends RuntimeException {

    public RerankingException(String message) {
        super(message);
    }

    public RerankingException(String message, Throwable cause) {
        super(message, cause);
    }
}
