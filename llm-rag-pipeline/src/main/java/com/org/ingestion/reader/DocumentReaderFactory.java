package com.org.ingestion.reader;

import com.org.ingestion.IngestionProperties;
import com.org.ingestion.excel.ExcelDocumentReader;
import com.org.ingestion.model.IngestedDocument;
import com.org.ingestion.ocr.OcrPdfAugmentor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a document {@link Resource} into one or more {@link IngestedDocument}s using the Spring AI
 * {@code DocumentReader} that matches the file extension. Each Spring AI {@link Document} produced by
 * the reader (a page, a markdown section, a JSON object, ...) becomes one {@link IngestedDocument},
 * preserving the reader's natural segmentation; downstream chunking refines it further.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentReaderFactory {

    private final IngestionProperties properties;
    private final OcrPdfAugmentor ocrPdfAugmentor;

    /** True when the extension is one this factory knows how to read. */
    public boolean supports(String fileName) {
        return sourceFor(extension(fileName)) != null;
    }

    public List<IngestedDocument> read(Resource resource) {
        String fileName = resource.getFilename() != null ? resource.getFilename() : "unknown";
        String ext = extension(fileName);
        String source = sourceFor(ext);
        if (source == null) {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }

        List<Document> documents = readerFor(ext, resource).get();
        if ("pdf".equals(ext)) {
            documents = ocrPdfAugmentor.augment(resource, documents); // OCR scanned pages (opt-in)
        }
        String identity = source + "#" + fileName;

        List<IngestedDocument> result = new ArrayList<>(documents.size());
        for (Document document : documents) {
            var metadata = new java.util.HashMap<String, Object>(document.getMetadata());
            metadata.put("fileName", fileName);
            metadata.put("identity", identity);
            result.add(new IngestedDocument(source, document.getText(), metadata));
        }
        log.info("Read {} segment(s) from {} (source={})", result.size(), fileName, source);
        return result;
    }

    // ── reader selection ───────────────────────────────────────────────────────

    private org.springframework.ai.document.DocumentReader readerFor(String ext, Resource resource) {
        return switch (ext) {
            case "pdf" -> "paragraph".equalsIgnoreCase(properties.getPdfReader())
                    ? new ParagraphPdfDocumentReader(resource)
                    : new PagePdfDocumentReader(resource);
            case "md", "markdown" ->
                    new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig());
            case "txt" -> new TextReader(resource);
            case "json" -> new JsonReader(resource);
            // Excel goes through POI for structured Markdown tables (Tika would flatten them).
            case "xlsx", "xls" -> new ExcelDocumentReader(resource);
            case "docx", "doc", "pptx", "ppt", "html", "htm" ->
                    new TikaDocumentReader(resource);
            default -> throw new IllegalArgumentException("Unsupported file type: ." + ext);
        };
    }

    /** Maps an extension to the pipeline source label (drives chunking + retrieval filtering). */
    private String sourceFor(String ext) {
        return switch (ext) {
            case "pdf" -> "PDF";
            case "md", "markdown" -> "WIKI";
            case "txt", "json", "docx", "doc", "pptx", "ppt", "xlsx", "xls", "html", "htm" -> "FILE";
            default -> null;
        };
    }

    private static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
