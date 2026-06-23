package com.org.retrieval;

import com.org.chunking.model.Chunk;
import com.org.retrieval.model.RetrievalResult;
import com.org.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class RetrievalServiceTest extends IntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceTest.class);

    @Autowired
    private RetrievalService retrievalService;

    @Test
    @DisplayName("Retrieves relevant chunks for a natural-language policy question")
    void retrieve_test() {
        RetrievalResult result = retrievalService.retrieve("What is the work from home policy");

        log.info("Retrieval result - chunks found: {}", result.getChunks().orElse(List.of()).size());
        for (Chunk chunk : result.getChunks().orElse(List.of())) {
            log.info("Metadata    : {}", chunk.metadata());
            log.info("Content     : {}", chunk.content());
        }

    }
}
