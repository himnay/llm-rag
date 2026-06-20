package com.org.retrieval.transform;

import com.org.retrieval.RetrievalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Generates several paraphrased variants of the query. {@link com.org.retrieval.RetrievalService}
 * retrieves for each variant and merges the result sets — improving recall on ambiguous or
 * broadly-phrased questions where a single phrasing might miss relevant chunks that use different
 * vocabulary. Delegates to Spring AI's {@link MultiQueryExpander} rather than hand-rolling the
 * prompt; always includes the original query (matching the previous behavior).
 */
@Slf4j
@Component
public class MultiQueryExpanderImpl implements QueryTransformer {

    private final MultiQueryExpander delegate;

    /**
     * Primary constructor used by Spring — reads count from {@code app.retrieval.query-transform.multi-query-count}.
     */
    @Autowired
    public MultiQueryExpanderImpl(ChatClient.Builder chatClientBuilder, RetrievalProperties properties) {
        this(chatClientBuilder, properties.getQueryTransform().getMultiQueryCount());
    }

    /**
     * Secondary constructor for tests and manual wiring.
     */
    public MultiQueryExpanderImpl(ChatClient.Builder chatClientBuilder, int count) {
        this.delegate = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(count)
                .includeOriginal(true)
                .build();
    }

    @Override
    public QueryTransformMode mode() {
        return QueryTransformMode.MULTI_QUERY;
    }

    @Override
    public List<String> transform(String query) {
        try {
            List<String> variants = delegate.expand(new Query(query)).stream().map(Query::text).toList();
            log.debug("MultiQueryExpand: '{}' -> {} variants", query, variants.size());
            return variants;
        } catch (Exception e) {
            log.warn("MultiQueryExpand failed ({}), using original query", e.getMessage());
            return List.of(query);
        }
    }
}
