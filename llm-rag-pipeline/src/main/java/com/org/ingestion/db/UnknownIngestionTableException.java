package com.org.ingestion.db;

public class UnknownIngestionTableException extends RuntimeException {

    public UnknownIngestionTableException(String tableName, String supported) {
        super("Unknown database table for ingestion: '" + tableName + "'. Supported tables: " + supported);
    }
}
