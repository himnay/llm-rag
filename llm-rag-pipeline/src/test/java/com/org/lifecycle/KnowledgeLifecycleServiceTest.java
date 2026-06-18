package com.org.lifecycle;

import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;
import com.org.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class KnowledgeLifecycleServiceTest extends IntegrationTest {
    @Autowired
    private KnowledgeLifecycleService knowledgeLifecycleService;

    @Test
    @DisplayName("Ingests a PDF knowledge source through the lifecycle service")
    void testIngestPdf() throws Exception {
        KnowledgeRequest request = KnowledgeRequest.builder()
                .sourceType(SourceType.PDF)
                .name("HR_Leave_Policy.pdf")
                .build();
        knowledgeLifecycleService.ingest(request);
    }


    @Test
    @DisplayName("Deletes a knowledge source through the lifecycle service")
    void testDeleteKnowledge() throws Exception {
        KnowledgeRequest request = KnowledgeRequest.builder()
                .sourceType(SourceType.PDF)
                .name("HR_Leave_Policy.pdf")
                .build();
        knowledgeLifecycleService.delete(request);
    }


    @Test
    @DisplayName("Ingests all configured knowledge sources")
    void testIngestAll() throws Exception {
        knowledgeLifecycleService.ingestAll();
    }

    @Test
    @DisplayName("Deletes all knowledge sources")
    void testDeleteAll() {
        knowledgeLifecycleService.deleteAll();
    }
}
