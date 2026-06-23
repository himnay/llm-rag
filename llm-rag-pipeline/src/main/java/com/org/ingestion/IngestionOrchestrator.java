package com.org.ingestion;

import com.org.ingestion.db.DatabaseIngestionService;
import com.org.ingestion.model.IngestedDocument;
import com.org.ingestion.pdf.PdfIngestionService;
import com.org.ingestion.wiki.WikiIngestionService;
import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionOrchestrator {

    private final PdfIngestionService pdfIngestionService;
    private final WikiIngestionService wikiIngestionService;
    private final DatabaseIngestionService databaseIngestionService;

    /**
     * Ingests the single named source described by {@code request} (PDF, wiki page, or DB table),
     * returning an empty list for unrecognized source types.
     */
    public List<IngestedDocument> ingest(KnowledgeRequest request) throws IOException {
        SourceType source = request.getSourceType();
        if (source.equals(SourceType.PDF)) {
            return pdfIngestionService.ingest(request.getName());
        }
        if (source.equals(SourceType.WIKI)) {
            return wikiIngestionService.ingest(request.getName());
        }
        if (source.equals(SourceType.DATABASE)) {
            return databaseIngestionService.ingest(request.getName());
        }
        return Collections.emptyList();
    }

    /**
     * Ingests every PDF, wiki file, and database row across all configured sources.
     */
    public List<IngestedDocument> ingestAll() throws IOException {
        List<IngestedDocument> docs = new ArrayList<>();
        docs.addAll(pdfIngestionService.ingestPdfs());
        docs.addAll(wikiIngestionService.ingestWikiFiles());
        docs.addAll(databaseIngestionService.ingestDatabaseContent());
        return docs;
    }

}
