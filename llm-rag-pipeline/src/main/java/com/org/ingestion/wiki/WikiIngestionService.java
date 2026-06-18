package com.org.ingestion.wiki;

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
 * Ingests markdown wiki pages from {@code app.ingestion.wiki-location} using Spring AI's
 * MarkdownDocumentReader (via {@link DocumentReaderFactory}). One {@link IngestedDocument} per section.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiIngestionService {

    private final IngestionProperties properties;
    private final DocumentReaderFactory readerFactory;
    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * Ingests the named markdown wiki file from {@code app.ingestion.wiki-location}.
     *
     * @throws IOException if the file does not exist there
     */
    public List<IngestedDocument> ingest(String fileName) throws IOException {
        Resource resource = resolver.getResource(properties.getWikiLocation() + fileName);
        if (!resource.exists()) {
            throw new IOException("Wiki file not found: " + properties.getWikiLocation() + fileName);
        }
        return readerFactory.read(resource);
    }

    /**
     * Ingests every {@code *.md} file under {@code app.ingestion.wiki-location}.
     */
    public List<IngestedDocument> ingestWikiFiles() throws IOException {
        List<IngestedDocument> docs = new ArrayList<>();
        for (Resource resource : resolver.getResources(properties.getWikiLocation() + "*.md")) {
            docs.addAll(readerFactory.read(resource));
        }
        log.info("Ingested {} wiki segment(s) from {}", docs.size(), properties.getWikiLocation());
        return docs;
    }
}
