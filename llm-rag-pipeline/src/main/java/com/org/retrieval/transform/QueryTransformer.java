package com.org.retrieval.transform;

import java.util.List;

/**
 * Pre-retrieval query transformation strategy. Returns one or more queries to search with:
 * single-output transformers (rewrite, HyDE, step-back) return a list of size 1, while expanders
 * (multi-query) return several. {@link com.org.retrieval.RetrievalService} retrieves for each
 * returned query and merges results by document ID, so multi-query recall improvements come for
 * free from existing infrastructure.
 */
public interface QueryTransformer {

    /** Transform or expand the input query. Never returns null or an empty list. */
    List<String> transform(String query);

    QueryTransformMode mode();
}
