package com.org.llm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LlmRagGraphApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring context can be assembled.
        // Requires a running Neo4j instance; run with -Dspring.profiles.active=test
        // and set NEO4J_PASSWORD / ANTHROPIC_API_KEY in the environment.
    }
}
