package com.org.retrieval.transform;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rewrites a messy or conversational user query into a clean, standalone search query. Delegates
 * to Spring AI's {@link RewriteQueryTransformer} rather than hand-rolling the prompt.
 */
@Slf4j
@Component
public class RewriteQueryTransformerImpl implements QueryTransformer {

    private final RewriteQueryTransformer delegate;

    public RewriteQueryTransformerImpl(ChatClient.Builder chatClientBuilder) {
        this.delegate = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();
    }

    @Override
    public QueryTransformMode mode() {
        return QueryTransformMode.REWRITE;
    }

    @Override
    public List<String> transform(String query) {
        try {
            Query result = delegate.transform(new Query(query));
            log.debug("QueryRewrite: '{}' -> '{}'", query, result.text());
            return List.of(result.text());
        } catch (Exception e) {
            log.warn("QueryRewrite failed ({}), using original query", e.getMessage());
            return List.of(query);
        }
    }
}
