package com.org.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Strategy for reading a specific {@link DocumentType} into a list of Spring AI {@link Document}s.
 * Each implementation handles one document type and is auto-discovered by {@link DocumentReaderFactory}.
 */
public interface DocumentReaderStrategy {

    /**
     * The document type this strategy handles.
     */
    DocumentType documentType();

    /**
     * Read {@code resource} and return one {@link Document} per natural segment (page, section, row…).
     */
    List<Document> read(Resource resource);
}
