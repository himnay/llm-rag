package com.org.ingestion.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads Markdown / wiki files, producing one {@link Document} per section.
 */
@Component
class MarkdownReaderStrategy implements DocumentReaderStrategy {

    @Override
    public DocumentType documentType() {
        return DocumentType.MARKDOWN;
    }

    @Override
    public List<Document> read(Resource resource) {
        return new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig()).get();
    }
}
