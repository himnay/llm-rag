package com.org.exception;

/**
 * Thrown when an Excel workbook cannot be parsed during ingestion.
 */
public class ExcelReadException extends RuntimeException {

    public ExcelReadException(String message) {
        super(message);
    }

    public ExcelReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
