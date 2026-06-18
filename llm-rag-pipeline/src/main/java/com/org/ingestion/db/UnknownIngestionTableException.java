package com.org.ingestion.db;

public class UnknownIngestionTableException extends RuntimeException {

    /**
     * Builds the exception message listing the unrecognized {@code tableName} and supported tables.
     */
    public UnknownIngestionTableException(String tableName, String supported) {
        super("Unknown database table for ingestion: '" + tableName + "'. Supported tables: " + supported);
    }
}
