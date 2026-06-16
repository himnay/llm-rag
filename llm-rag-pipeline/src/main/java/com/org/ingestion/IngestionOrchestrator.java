package com.org.ingestion;

import com.org.ingestion.db.DatabaseIngestionService;
import com.org.ingestion.model.IngestedDocument;
import com.org.ingestion.pdf.PdfIngestionService;
import com.org.ingestion.wiki.WikiIngestionService;
import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IngestionOrchestrator {

    private final PdfIngestionService pdfIngestionService;
    private final WikiIngestionService wikiIngestionService;
    private final DatabaseIngestionService databaseIngestionService;

    public List<IngestedDocument> ingest(KnowledgeRequest request) throws Exception {
        SourceType source = request.sourceType();
        if (source.equals(SourceType.PDF)) {
            return pdfIngestionService.ingest(request.name());
        }
        if (source.equals(SourceType.WIKI)) {
            return wikiIngestionService.ingest(request.name());
        }
        if (source.equals(SourceType.DATABASE)) {
            return databaseIngestionService.ingest(request.name());
        }
        return Collections.emptyList();
    }

    public List<IngestedDocument> ingestAll() throws Exception {
        List<IngestedDocument> docs = new ArrayList<>();
        docs.addAll(pdfIngestionService.ingestPdfs());
        docs.addAll(wikiIngestionService.ingestWikiFiles());
        docs.addAll(databaseIngestionService.ingestDatabaseContent());
        return docs;
    }

}
