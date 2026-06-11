package com.org.lifecycle;
import com.org.support.IntegrationTest;

import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class KnowledgeLifecycleServiceTest extends IntegrationTest {
    @Autowired
    private KnowledgeLifecycleService knowledgeLifecycleService;

    @Test
    void testIngestPdf() throws Exception {
        KnowledgeRequest request = KnowledgeRequest.builder()
                .sourceType(SourceType.PDF)
                .name("HR_Leave_Policy.pdf")
                .build();
        knowledgeLifecycleService.ingest(request);
    }


    @Test
    void testDeleteKnowledge() throws Exception {
        KnowledgeRequest request = KnowledgeRequest.builder()
                .sourceType(SourceType.PDF)
                .name("HR_Leave_Policy.pdf")
                .build();
        knowledgeLifecycleService.delete(request);
    }


    @Test
    void testIngestAll() throws Exception {
        knowledgeLifecycleService.ingestAll();
    }

    @Test
    void testDeleteAll() {
        knowledgeLifecycleService.deleteAll();
    }
}
