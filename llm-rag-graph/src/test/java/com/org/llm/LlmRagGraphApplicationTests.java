package com.org.llm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LlmRagGraphApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context can be assembled. The test profile disables
        // graph seeding and supplies a dummy Anthropic key, so no running Neo4j
        // instance or real API key is required.
    }
}
