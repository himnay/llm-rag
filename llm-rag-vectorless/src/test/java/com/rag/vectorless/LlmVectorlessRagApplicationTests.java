package com.rag.vectorless;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LlmVectorlessRagApplicationTests {

    @Test
    @DisplayName("Loads the Spring application context successfully")
    void contextLoads() {
    }

}
