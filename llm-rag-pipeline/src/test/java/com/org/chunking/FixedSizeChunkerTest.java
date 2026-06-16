package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.ingestion.IngestionOrchestrator;
import com.org.ingestion.model.IngestedDocument;
import com.org.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class FixedSizeChunkerTest extends IntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(FixedSizeChunkerTest.class);

    @Autowired
    private IngestionOrchestrator ingestionOrchestrator;

    @Autowired
    private FixedSizeChunker chunker;

    @Test
    public void chunkerTest() throws Exception {
        List<IngestedDocument> documents = ingestionOrchestrator.ingestAll();

        IngestedDocument document = documents.get(0);

        log.info("========== NO OVERLAP ==========");
        List<Chunk> chunks = chunker.chunk(document, 500);
        printChunks(document, chunks);

        log.info("========== WITH OVERLAP (100 chars) ==========");
        List<Chunk> overlapChunks = chunker.chunk(document, 500, 100);
        printChunks(document, overlapChunks);

    }

    private static void printChunks(IngestedDocument document, List<Chunk> chunks) {
        log.info("Source: {}", document.source());
        log.info("Original length: {}", document.content().length());
        log.info("Total chunks: {}", chunks.size());

        for (Chunk chunk : chunks) {
            log.info("---- Chunk {} ----", chunk.chunkIndex());
            log.info(chunk.content());
        }
    }
}
