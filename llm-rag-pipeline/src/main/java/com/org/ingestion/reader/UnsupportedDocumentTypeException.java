package com.org.ingestion.reader;

/**
 * Thrown when the ingestion pipeline encounters a file whose type has no registered reader strategy.
 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    /**
     * Builds the exception message naming the unsupported {@code fileName}.
     */
    public UnsupportedDocumentTypeException(String fileName) {
        super("Unsupported document type: " + fileName);
    }
}
