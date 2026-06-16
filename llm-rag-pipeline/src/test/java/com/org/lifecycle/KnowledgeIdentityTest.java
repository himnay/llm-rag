package com.org.lifecycle;

import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;
import com.org.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KnowledgeIdentityTest extends IntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIdentityTest.class);

    @Test
    void testSourceIdentity() {
        KnowledgeRequest request = KnowledgeRequest.builder()
                .sourceType(SourceType.PDF)
                .name("HR_Leave_Policy.pdf")
                .build();
        log.info("Identity = {}", KnowledgeIdentity.from(request));

        KnowledgeRequest dbRequest = KnowledgeRequest.builder()
                .sourceType(SourceType.DATABASE)
                .name("faqs")
                .build();
        log.info("Identity = {}", KnowledgeIdentity.from(dbRequest));

    }
}
