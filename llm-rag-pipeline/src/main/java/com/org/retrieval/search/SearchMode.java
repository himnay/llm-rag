package com.org.retrieval.search;

/**
 * First-stage candidate search technique, selected via {@code app.retrieval.search.mode}.
 *
 * <ul>
 *   <li>{@link #VECTOR} — dense cosine-similarity kNN over embeddings (OpenSearch knn index).
 *       Best for paraphrased / semantic queries. The default.</li>
 *   <li>{@link #KEYWORD} — lexical BM25 full-text match on the chunk content. Best for exact
 *       terms: error codes, names, IDs. No embedding call.</li>
 *   <li>{@link #HYBRID} — runs both and fuses the rankings with Reciprocal Rank Fusion; robust
 *       when queries mix natural language with exact terms.</li>
 * </ul>
 */
public enum SearchMode {
    VECTOR,
    KEYWORD,
    HYBRID
}
