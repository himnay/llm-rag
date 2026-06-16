package com.org.generation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.generation")
public class GenerationProperties {

    private final Advisor advisor = new Advisor();
    /**
     * Enable the /api/v1/generate endpoint. Disabled by default — this service is retrieval-only
     * by design; enable only when a downstream llm-gateway is not in use.
     */
    private boolean enabled = false;
    /**
     * Generation mode: {@code manual} = hand-built prompt pipeline (Section 7 pattern);
     * {@code advisor} = Spring AI QuestionAnswerAdvisor (Section 12 pattern).
     */
    private String mode = "manual";
    /**
     * Default topK for retrieval during generation.
     */
    private int topK = 5;
    /**
     * Include source citations in the response.
     */
    private boolean includeCitations = true;
    /**
     * Evaluate faithfulness after every generation (adds one extra LLM call).
     */
    private boolean evaluateFaithfulness = false;

    @Data
    public static class Advisor {
        private double similarityThreshold = 0.7;
    }
}
