package com.org.generation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.generation")
public class GenerationProperties {

    private final Advisor advisor = new Advisor();
    private final Judge judge = new Judge();
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
    private boolean evaluateFaithfulness = true;

    @Data
    public static class Advisor {
        private double similarityThreshold = 0.7;
    }

    @Data
    public static class Judge {
        /**
         * Pre-generation LLM-as-judge — skip the final LLM call (returning
         * {@link #insufficientAnswer}) when retrieved context is judged insufficient.
         */
        private boolean enabled = true;
        private String insufficientAnswer =
                "I don't have enough information in the available context to answer that question.";
    }
}
