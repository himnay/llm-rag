package com.org.ingestion;

import com.org.ingestion.model.IngestedDocument;
import com.org.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class IngestionOrchestratorTest extends IntegrationTest {

    @Autowired
    private IngestionOrchestrator ingestionOrchestrator;

    @Test
    @DisplayName("Ingests all configured sources and prints each document's source and content")
    void ingestAll() throws Exception {
        List<IngestedDocument> docs = ingestionOrchestrator.ingestAll();
        System.out.println("Total docs = " + docs.size());

        docs.forEach(doc -> {
            System.out.println("SOURCE = " + doc.source());
            System.out.println(doc.content());
            System.out.println("----");
        });
    }
}
