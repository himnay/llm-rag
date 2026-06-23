package com.org.ingestion.reader;

import com.org.ingestion.model.IngestedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the correct {@link DocumentReaderStrategy} for a file based on its extension
 * ({@link DocumentType} enum) and delegates reading to it.
 *
 * <p>Strategy implementations are auto-discovered as Spring beans. Adding support for a new
 * file type only requires a new {@link DocumentReaderStrategy} component — no changes here.</p>
 */
@Slf4j
@Component
public class DocumentReaderFactory {

    private final Map<DocumentType, DocumentReaderStrategy> strategies;

    /**
     * Indexes the auto-discovered strategies by the {@link DocumentType} each one handles.
     */
    public DocumentReaderFactory(List<DocumentReaderStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(DocumentReaderStrategy::documentType, Function.identity()));
    }

    /**
     * Returns {@code true} when the factory has a registered strategy for this file's extension.
     */
    public boolean supports(String fileName) {
        return DocumentType.of(extension(fileName)).isPresent();
    }

    /**
     * Read {@code resource} into a list of {@link IngestedDocument}s using the strategy
     * registered for its extension. Each Spring AI {@link Document} becomes one entry,
     * preserving the reader's natural segmentation (page, section, row…).
     *
     * @throws UnsupportedDocumentTypeException if no strategy covers the file's extension
     */
    public List<IngestedDocument> read(Resource resource) {
        String fileName = resource.getFilename() != null ? resource.getFilename() : "unknown";
        DocumentType type = DocumentType.of(extension(fileName))
                .orElseThrow(() -> new UnsupportedDocumentTypeException(fileName));

        List<Document> documents = strategies.get(type).read(resource);
        String identity = type.sourceLabel() + "#" + fileName;

        List<IngestedDocument> result = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("fileName", fileName);
            metadata.put("identity", identity);
            result.add(new IngestedDocument(type.sourceLabel(), doc.getText(), metadata));
        }
        log.info("Read {} segment(s) from {} (type={})", result.size(), fileName, type);
        return result;
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
