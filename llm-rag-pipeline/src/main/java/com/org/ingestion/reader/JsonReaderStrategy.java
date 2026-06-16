package com.org.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads JSON files, producing one {@link Document} per top-level JSON object.
 */
@Component
class JsonReaderStrategy implements DocumentReaderStrategy {

    @Override
    public DocumentType documentType() {
        return DocumentType.JSON;
    }

    @Override
    public List<Document> read(Resource resource) {
        return new JsonReader(resource).get();
    }
}
