package com.org.retrieval;

import com.org.retrieval.search.SearchMode;
import com.org.retrieval.transform.QueryTransformMode;

/**
 * The two per-query retrieval decisions {@link RetrievalStrategyClassifier} (or static config)
 * resolves before search: which {@link SearchMode} to run, and which {@link QueryTransformMode}
 * to apply beforehand.
 */
public record RetrievalStrategy(SearchMode searchMode, QueryTransformMode transformMode) {
}
