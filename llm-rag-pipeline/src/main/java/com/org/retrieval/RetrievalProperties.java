package com.org.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning knobs for retrieval and the post-processing filter/ranking chain (prefix
 * {@code app.retrieval}). Sensible defaults keep behaviour close to a plain top-k search; the
 * heavier techniques (MMR, cross-encoder rerank) are opt-in.
 */
@Validated
@ConfigurationProperties(prefix = "app.retrieval")
public class RetrievalProperties {

    /** Final number of chunks returned when the caller doesn't specify one. */
    private int defaultTopK = 10;

    /** Over-fetch multiplier: fetch {@code topK * factor} candidates before post-filtering. */
    private int overFetchFactor = 3;

    /** Drop hits below this cosine similarity (0 = disabled). Pushed down into the vector query. */
    private double similarityThreshold = 0.0;

    private final Search search = new Search();
    private final Length length = new Length();
    private final Dedup dedup = new Dedup();
    private final Mmr mmr = new Mmr();
    private final Rerank rerank = new Rerank();

    /** First-stage candidate search ({@code com.org.retrieval.search}). */
    public static class Search {
        /** vector (cosine kNN) | keyword (BM25 full-text) | hybrid (RRF fusion of both). */
        private com.org.retrieval.search.SearchMode mode = com.org.retrieval.search.SearchMode.VECTOR;

        public com.org.retrieval.search.SearchMode getMode() { return mode; }
        public void setMode(com.org.retrieval.search.SearchMode mode) { this.mode = mode; }
    }

    /** Length filtering: drop chunks outside the LLM-friendly size band (0 = unbounded). */
    public static class Length {
        private int minChars = 0;
        private int maxChars = 0;

        public int getMinChars() { return minChars; }
        public void setMinChars(int minChars) { this.minChars = minChars; }
        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
    }

    /** Retrieval-time near-duplicate collapsing. */
    public static class Dedup {
        private boolean enabled = true;
        private double threshold = 0.95;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
    }

    /** Maximal Marginal Relevance diversity. */
    public static class Mmr {
        private boolean enabled = false;
        /** Relevance-vs-diversity trade-off: 1.0 = pure relevance, 0.0 = pure diversity. */
        private double lambda = 0.7;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getLambda() { return lambda; }
        public void setLambda(double lambda) { this.lambda = lambda; }
    }

    /**
     * Opt-in second-stage reranking ({@code com.org.retrieval.rerank}). {@code strategy} picks the
     * technique; {@code base-url}/{@code model}/{@code api-key} only apply to {@code cross-encoder}.
     */
    public static class Rerank {
        private boolean enabled = false;
        /** Which reranking technique to apply (cross-encoder | bi-encoder | llm-pointwise | llm-listwise | bm25 | rrf). */
        private com.org.retrieval.rerank.RerankStrategy strategy =
                com.org.retrieval.rerank.RerankStrategy.CROSS_ENCODER;
        private String baseUrl = "https://api.cohere.ai";
        private String model = "rerank-english-v3.0";
        private String apiKey = "";
        /** Re-score at most this many candidates (cost control); 0 = all. */
        private int topN = 0;
        /** Read timeout for the external cross-encoder call (fail-open on expiry). */
        private java.time.Duration timeout = java.time.Duration.ofSeconds(10);
        /** Drop re-scored chunks below this relevance (0 = off). Scales differ per strategy. */
        private double minScore = 0.0;
        private final Cache cache = new Cache();
        private final Breaker breaker = new Breaker();

        /** Per-chunk rerank-score cache — only used by the costly strategies (API/LLM/embedding). */
        public static class Cache {
            private boolean enabled = true;
            private int maxSize = 1000;
            private java.time.Duration ttl = java.time.Duration.ofMinutes(10);

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public int getMaxSize() { return maxSize; }
            public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
            public java.time.Duration getTtl() { return ttl; }
            public void setTtl(java.time.Duration ttl) { this.ttl = ttl; }
        }

        /** Circuit breaker: skip reranking while the backing vendor/model keeps failing. */
        public static class Breaker {
            private int failureThreshold = 3;
            private java.time.Duration cooldown = java.time.Duration.ofSeconds(30);

            public int getFailureThreshold() { return failureThreshold; }
            public void setFailureThreshold(int failureThreshold) { this.failureThreshold = failureThreshold; }
            public java.time.Duration getCooldown() { return cooldown; }
            public void setCooldown(java.time.Duration cooldown) { this.cooldown = cooldown; }
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getMinScore() { return minScore; }
        public void setMinScore(double minScore) { this.minScore = minScore; }
        public Cache getCache() { return cache; }
        public Breaker getBreaker() { return breaker; }
        public com.org.retrieval.rerank.RerankStrategy getStrategy() { return strategy; }
        public void setStrategy(com.org.retrieval.rerank.RerankStrategy strategy) { this.strategy = strategy; }
        public java.time.Duration getTimeout() { return timeout; }
        public void setTimeout(java.time.Duration timeout) { this.timeout = timeout; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getTopN() { return topN; }
        public void setTopN(int topN) { this.topN = topN; }
    }

    public int getDefaultTopK() { return defaultTopK; }
    public void setDefaultTopK(int defaultTopK) { this.defaultTopK = defaultTopK; }
    public int getOverFetchFactor() { return overFetchFactor; }
    public void setOverFetchFactor(int overFetchFactor) { this.overFetchFactor = overFetchFactor; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public Search getSearch() { return search; }
    public Length getLength() { return length; }
    public Dedup getDedup() { return dedup; }
    public Mmr getMmr() { return mmr; }
    public Rerank getRerank() { return rerank; }
}
