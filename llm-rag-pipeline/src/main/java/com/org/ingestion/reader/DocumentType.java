package com.org.ingestion.reader;

import java.util.*;

/**
 * Canonical document types recognized by the ingestion pipeline.
 * Each entry owns its supported file extensions and the source label
 * used to tag chunks for chunking-strategy selection and retrieval filtering.
 */
public enum DocumentType {

    PDF("PDF", "pdf"),
    MARKDOWN("WIKI", "md", "markdown"),
    TEXT("FILE", "txt"),
    JSON("FILE", "json"),
    EXCEL("FILE", "xlsx", "xls"),
    OFFICE("FILE", "docx", "doc", "pptx", "ppt", "html", "htm");

    private static final Map<String, DocumentType> BY_EXTENSION;

    static {
        Map<String, DocumentType> map = new HashMap<>();
        for (DocumentType type : values()) {
            for (String ext : type.extensions) {
                map.put(ext, type);
            }
        }
        BY_EXTENSION = Map.copyOf(map);
    }

    private final String sourceLabel;
    private final Set<String> extensions;

    DocumentType(String sourceLabel, String... extensions) {
        this.sourceLabel = sourceLabel;
        this.extensions = Set.of(extensions);
    }

    /**
     * Resolve a {@link DocumentType} from a file extension (case-insensitive).
     */
    public static Optional<DocumentType> of(String extension) {
        return Optional.ofNullable(BY_EXTENSION.get(extension.toLowerCase(Locale.ROOT)));
    }

    /**
     * Pipeline source label that drives chunking strategy selection and retrieval filtering.
     */
    public String sourceLabel() {
        return sourceLabel;
    }
}
