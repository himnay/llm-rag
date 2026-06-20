package com.org.retrieval.transform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rewrites a messy or conversational user query into a clean, standalone search query using the
 * LLM. Particularly valuable in multi-turn chat where the question depends on prior context.
 *
 * <p>Maps to Spring AI's {@code RewriteQueryTransformer}; implemented directly with
 * {@link ChatClient} for full control over the prompt.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RewriteQueryTransformerImpl implements QueryTransformer {

    private static final String SYSTEM = """
                                            You are a search query optimization assistant. 
                                            Rewrite the user's question into a concise, standalone search query optimised for semantic retrieval. 
                                            Remove conversational filler. Return ONLY the rewritten query, nothing else.
                                        """;

    private final ChatClient chatClient;

    @Override
    public QueryTransformMode mode() {
        return QueryTransformMode.REWRITE;
    }

    @Override
    public List<String> transform(String query) {
        try {
            String rewritten = chatClient.prompt()
                    .system(SYSTEM)
                    .user(query)
                    .call()
                    .content();
            String result = (rewritten == null || rewritten.isBlank()) ? query : rewritten.strip();
            log.debug("QueryRewrite: '{}' -> '{}'", query, result);
            return List.of(result);
        } catch (Exception e) {
            log.warn("QueryRewrite failed ({}), using original query", e.getMessage());
            return List.of(query);
        }
    }
}
