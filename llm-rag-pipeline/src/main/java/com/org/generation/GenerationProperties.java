package com.org.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.generation")
public class GenerationProperties {

    /** Enable the /api/v1/generate endpoint. Disabled by default — this service is retrieval-only
     *  by design; enable only when a downstream llm-gateway is not in use. */
    private boolean enabled = false;

    /** Generation mode: {@code manual} = hand-built prompt pipeline (Section 7 pattern);
     *  {@code advisor} = Spring AI QuestionAnswerAdvisor (Section 12 pattern). */
    private String mode = "manual";

    /** Default topK for retrieval during generation. */
    private int topK = 5;

    /** Include source citations in the response. */
    private boolean includeCitations = true;

    /** Evaluate faithfulness after every generation (adds one extra LLM call). */
    private boolean evaluateFaithfulness = false;

    private final Advisor advisor = new Advisor();

    public static class Advisor {
        private double similarityThreshold = 0.7;

        public double getSimilarityThreshold() { return similarityThreshold; }
        public void setSimilarityThreshold(double st) { this.similarityThreshold = st; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public boolean isIncludeCitations() { return includeCitations; }
    public void setIncludeCitations(boolean includeCitations) { this.includeCitations = includeCitations; }
    public boolean isEvaluateFaithfulness() { return evaluateFaithfulness; }
    public void setEvaluateFaithfulness(boolean evaluateFaithfulness) { this.evaluateFaithfulness = evaluateFaithfulness; }
    public Advisor getAdvisor() { return advisor; }
}
