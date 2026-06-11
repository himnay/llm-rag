package com.org.chunking;
import com.org.support.IntegrationTest;

import com.org.chunking.model.Chunk;
import com.org.ingestion.IngestionOrchestrator;
import com.org.ingestion.model.IngestedDocument;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class PdfPragmaticChunkerTest extends IntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(WikiSemanticChunkerTest.class);

    @Autowired
    private IngestionOrchestrator ingestionOrchestrator;

    @Autowired
    private PdfPragmaticChunker pdfChunker;

    @Test
    void chunk_pdf_with_pragmatic_strategy() throws Exception {
        List<IngestedDocument> documents = ingestionOrchestrator.ingestAll();

        IngestedDocument pdfDoc = documents.stream()
                .filter(d -> d.source().equals("PDF"))
                .findFirst()
                .orElseThrow();

        List<Chunk> chunks = pdfChunker.chunk(pdfDoc);
        log.info("PDF Source: {}", pdfDoc.source());
        log.info("Total chunks: {}", chunks.size());

        for (Chunk chunk : chunks) {
            log.info("---- Chunk {} ----", chunk.chunkIndex());
            log.info("Metadata: {}", chunk.metadata());
            log.info(chunk.content());
        }
    }
}
