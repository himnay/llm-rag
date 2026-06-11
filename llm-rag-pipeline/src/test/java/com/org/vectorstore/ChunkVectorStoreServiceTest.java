package com.org.vectorstore;
import com.org.support.IntegrationTest;

import com.org.chunking.ChunkingOrchestrator;
import com.org.chunking.model.Chunk;
import com.org.ingestion.IngestionOrchestrator;
import com.org.ingestion.model.IngestedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class ChunkVectorStoreServiceTest extends IntegrationTest {
    @Autowired
    private IngestionOrchestrator ingestionOrchestrator;

    @Autowired
    private ChunkingOrchestrator chunkingOrchestrator;

    @Autowired
    private ChunkVectorStoreService vectorStoreService;

    @Test
    void testVectorStore() throws Exception {
        List<IngestedDocument> documents = ingestionOrchestrator.ingestAll();

        List<Chunk> chunksToStore = new ArrayList<>();
        for (IngestedDocument document : documents) {
            List<Chunk> chunks = chunkingOrchestrator.chunk(document);
            chunksToStore.addAll(chunks);
        }
        vectorStoreService.store(chunksToStore);
    }
}
