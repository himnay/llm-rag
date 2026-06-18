package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.ingestion.IngestionOrchestrator;
import com.org.ingestion.model.IngestedDocument;
import com.org.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class ChunkingOrchestratorTest extends IntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(ChunkingOrchestratorTest.class);

    @Autowired
    private IngestionOrchestrator ingestionOrchestrator;

    @Autowired
    private ChunkingOrchestrator chunkingOrchestrator;

    @Test
    @DisplayName("Chunks every ingested document via the chunking orchestrator")
    void testAllChunks() throws Exception {
        List<IngestedDocument> documents = ingestionOrchestrator.ingestAll();
        for (IngestedDocument ingestedDocument : documents) {
            List<Chunk> chunks = chunkingOrchestrator.chunk(ingestedDocument);

            log.info("====================================");
            log.info("SOURCE : {}", ingestedDocument.source());
            log.info("CHUNKS : {}", chunks.size());

            for (Chunk chunk : chunks) {
                log.info("Chunk index : {}", chunk.chunkIndex());
                log.info("Metadata    : {}", chunk.metadata());
                log.info("Content     : {}", chunk.content());
            }
        }
    }
}
