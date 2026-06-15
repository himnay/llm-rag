package com.org.retrieval.transform;

public enum QueryTransformMode {
    /** No transformation — query is used as-is. */
    NONE,

    /** Rewrite the query into a cleaner, standalone search query using the LLM. */
    REWRITE,

    /** Generate several paraphrased variants of the query and union the results. */
    MULTI_QUERY,

    /** Generate a hypothetical answer document and use its text for embedding-based retrieval (HyDE). */
    HYDE,

    /** Generate a more general version of the query to retrieve broader context first. */
    STEP_BACK
}
