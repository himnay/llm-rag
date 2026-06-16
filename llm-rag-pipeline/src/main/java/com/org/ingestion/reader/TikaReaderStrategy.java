package com.org.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads Office documents and HTML files via Apache Tika (catch-all for DOCX, PPTX, HTML, etc.).
 */
@Component
class TikaReaderStrategy implements DocumentReaderStrategy {

    @Override
    public DocumentType documentType() {
        return DocumentType.OFFICE;
    }

    @Override
    public List<Document> read(Resource resource) {
        return new TikaDocumentReader(resource).get();
    }
}
