package com.org.ingestion.pdf;

import com.org.ingestion.IngestionProperties;
import com.org.ingestion.model.IngestedDocument;
import com.org.ingestion.reader.DocumentReaderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingests PDFs from {@code app.ingestion.pdf-location} using Spring AI's PDF reader
 * (via {@link DocumentReaderFactory}). One {@link IngestedDocument} per page (or paragraph).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfIngestionService {

    private final IngestionProperties properties;
    private final DocumentReaderFactory readerFactory;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * Ingests the named PDF from {@code app.ingestion.pdf-location}.
     *
     * @throws IOException if the file does not exist there
     */
    public List<IngestedDocument> ingest(String fileName) throws IOException {
        Resource resource = resolver.getResource(properties.getPdfLocation() + fileName);
        if (!resource.exists()) {
            throw new IOException("PDF not found: " + properties.getPdfLocation() + fileName);
        }
        return readerFactory.read(resource);
    }

    /**
     * Ingests every {@code *.pdf} file under {@code app.ingestion.pdf-location}.
     */
    public List<IngestedDocument> ingestPdfs() throws IOException {
        List<IngestedDocument> docs = new ArrayList<>();
        for (Resource resource : resolver.getResources(properties.getPdfLocation() + "*.pdf")) {
            docs.addAll(readerFactory.read(resource));
        }
        log.info("Ingested {} PDF segment(s) from {}", docs.size(), properties.getPdfLocation());
        return docs;
    }
}
