package com.org.ingestion.reader;

/**
 * Thrown when the ingestion pipeline encounters a file whose type has no registered reader strategy.
 */
public class UnsupportedDocumentTypeException extends RuntimeException {

    public UnsupportedDocumentTypeException(String fileName) {
        super("Unsupported document type: " + fileName);
    }
}
