package com.org.ingestion;
import com.org.support.IntegrationTest;

import com.org.ingestion.model.IngestedDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class IngestionOrchestratorTest extends IntegrationTest {
    
    @Autowired
    private IngestionOrchestrator ingestionOrchestrator;

    @Test
    void ingestAll() throws Exception{
        List<IngestedDocument> docs = ingestionOrchestrator.ingestAll();
        System.out.println("Total docs = " + docs.size());

        docs.forEach(doc -> {
            System.out.println("SOURCE = " + doc.source());
            System.out.println(doc.content());
            System.out.println("----");
        });
    }
}
