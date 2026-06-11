package com.org.chunking.strategy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chunking configuration ({@code app.chunking.*}).
 *
 * <p>{@code strategy=auto} (default) uses per-source defaults in {@code ChunkingOrchestrator}
 * (PDF→recursive, WIKI→markdown, DB→whole-row, other→token). Set it to a concrete strategy name
 * ({@code fixed|recursive|token|semantic|markdown|llm}) to force one for all sources.</p>
 */
@ConfigurationProperties(prefix = "app.chunking")
public class ChunkingProperties {

    private String strategy = "auto";
    private int maxChars = 1000;
    private int overlap = 150;
    private final Token token = new Token();
    private final Semantic semantic = new Semantic();

    public static class Token {
        private int chunkSize = 800; // tokens
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
    }

    public static class Semantic {
        /** Cosine-similarity threshold below which a new chunk starts. */
        private double threshold = 0.8;
        private int maxChars = 1500;
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
    }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public int getMaxChars() { return maxChars; }
    public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
    public int getOverlap() { return overlap; }
    public void setOverlap(int overlap) { this.overlap = overlap; }
    public Token getToken() { return token; }
    public Semantic getSemantic() { return semantic; }
}
