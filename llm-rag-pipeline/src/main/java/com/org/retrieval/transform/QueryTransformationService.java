package com.org.retrieval.transform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Selects and applies the configured {@link QueryTransformer} before retrieval. Returns one or more
 * transformed query strings; {@link com.org.retrieval.RetrievalService} searches for each and merges
 * results. When {@code NONE} is configured (the default), the original query is returned unchanged
 * and no LLM call is made.
 */
@Slf4j
@Service
public class QueryTransformationService {

    private final Map<QueryTransformMode, QueryTransformer> transformers;

    /**
     * Indexes the auto-discovered transformers by the {@link QueryTransformMode} each one handles.
     */
    public QueryTransformationService(List<QueryTransformer> transformerList) {
        this.transformers = new EnumMap<>(QueryTransformMode.class);
        transformerList.forEach(t -> this.transformers.put(t.mode(), t));
    }

    /**
     * Apply the given {@code mode} transformer to {@code query}. Returns the list of query strings
     * to retrieve for (one for single-output modes, many for {@code MULTI_QUERY}).
     */
    public List<String> transform(String query, QueryTransformMode mode) {
        if (mode == null || mode == QueryTransformMode.NONE) {
            return List.of(query);
        }
        QueryTransformer transformer = transformers.get(mode);
        if (transformer == null) {
            log.warn("No QueryTransformer registered for mode {} — falling back to NONE", mode);
            return List.of(query);
        }
        log.debug("Applying {} query transformation to: '{}'", mode, query);
        try {
            return transformer.transform(query);
        } catch (Exception e) {
            log.warn("Query transform failed, falling back to original query: {}", e.getMessage());
            return List.of(query);
        }
    }
}
