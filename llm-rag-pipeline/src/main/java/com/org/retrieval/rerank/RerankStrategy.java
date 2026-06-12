package com.org.retrieval.rerank;

/**
 * The reranking technique applied by {@link RerankingPostProcessor}, selected via
 * {@code app.retrieval.rerank.strategy} (Spring's relaxed binding accepts e.g. {@code cross-encoder}).
 *
 * <ul>
 *   <li>{@link #CROSS_ENCODER} — pointwise neural cross-encoder via an external Cohere-compatible
 *       {@code /v1/rerank} API. Highest quality, needs an API key.</li>
 *   <li>{@link #BI_ENCODER} — re-embeds query + candidates with the local {@code EmbeddingModel}
 *       and re-scores by cosine similarity. No extra vendor, one embedding round-trip.</li>
 *   <li>{@link #LLM_POINTWISE} — the chat LLM grades each candidate's relevance independently
 *       (0–100). One LLM call per candidate; cap with {@code top-n}.</li>
 *   <li>{@link #LLM_LISTWISE} — RankGPT-style: the chat LLM sees all candidates at once and returns
 *       a permutation. One LLM call total.</li>
 *   <li>{@link #BM25} — local lexical BM25 (Okapi) over the candidate set. Free and fast; strong
 *       on exact keyword/identifier queries where embeddings are weak.</li>
 *   <li>{@link #RRF} — Reciprocal Rank Fusion of the vector-similarity ranking and the BM25
 *       ranking (hybrid). Free and fast, no model calls.</li>
 * </ul>
 */
public enum RerankStrategy {
    CROSS_ENCODER(true),
    BI_ENCODER(true),
    LLM_POINTWISE(true),
    LLM_LISTWISE(true),
    BM25(false),
    RRF(false);

    private final boolean costly;

    RerankStrategy(boolean costly) {
        this.costly = costly;
    }

    /** Whether re-scoring pays a network/model call — score caching is only worth it when true. */
    public boolean isCostly() {
        return costly;
    }
}
