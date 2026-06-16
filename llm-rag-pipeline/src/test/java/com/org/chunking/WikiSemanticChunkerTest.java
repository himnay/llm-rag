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

public class WikiSemanticChunkerTest extends IntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(WikiSemanticChunkerTest.class);
    @Autowired
    private IngestionOrchestrator ingestionOrchestrator;

    @Autowired
    private FixedSizeChunker chunker;

    @Autowired
    private WikiSemanticChunker wikiSemanticChunker;

    private static void printChunks(IngestedDocument document, List<Chunk> chunks) {
        log.info("Source: {}", document.source());
        log.info("Original length: {}", document.content().length());
        log.info("Total chunks: {}", chunks.size());

        for (Chunk chunk : chunks) {
            log.info("---- Chunk {} ----", chunk.chunkIndex());
            log.info(chunk.content());
        }
    }

    @Test
    public void testChunker() throws Exception {
        List<IngestedDocument> documents = ingestionOrchestrator.ingestAll();

        // pick a wiki document
        IngestedDocument wikiDoc = documents.stream()
                .filter(d -> d.source().contains("WIKI"))
                .findFirst()
                .orElseThrow();

        log.info("========== FIXED SIZE CHUNKING ==========");
        List<Chunk> fixedChunks = chunker.chunk(wikiDoc, 500, 100);
        printChunks(wikiDoc, fixedChunks);

        log.info("========== SEMANTIC (WIKI) CHUNKING ==========");
        List<Chunk> semanticChunks = wikiSemanticChunker.chunk(wikiDoc);
        printChunks(wikiDoc, semanticChunks);
    }
}
