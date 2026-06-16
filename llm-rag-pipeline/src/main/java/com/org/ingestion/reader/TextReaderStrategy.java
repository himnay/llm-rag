package com.org.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads plain-text files as a single {@link Document}.
 */
@Component
class TextReaderStrategy implements DocumentReaderStrategy {

    @Override
    public DocumentType documentType() {
        return DocumentType.TEXT;
    }

    @Override
    public List<Document> read(Resource resource) {
        return new TextReader(resource).get();
    }
}
