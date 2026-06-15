package com.org.retrieval.transform;

import com.org.retrieval.RetrievalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates several paraphrased variants of the query, one per line. {@link
 * com.org.retrieval.RetrievalService} retrieves for each variant and merges the result sets —
 * improving recall on ambiguous or broadly-phrased questions where a single phrasing might miss
 * relevant chunks that use different vocabulary.
 *
 * <p>Maps to Spring AI's {@code MultiQueryExpander}; implemented directly with
 * {@link ChatClient} for full control.</p>
 */
@Slf4j
@Component
public class MultiQueryExpanderImpl implements QueryTransformer {

    private static final String SYSTEM_TEMPLATE =
            "You are a search query assistant. Generate %d distinct paraphrases of the user's question "
            + "to improve retrieval coverage. Each paraphrase should capture the same intent but use "
            + "different vocabulary or structure. Output ONLY the paraphrases, one per line, no bullets.";

    private final ChatClient chatClient;
    private final int count;

    /** Primary constructor used by Spring — reads count from {@code app.retrieval.query-transform.multi-query-count}. */
    @Autowired
    public MultiQueryExpanderImpl(ChatClient chatClient, RetrievalProperties properties) {
        this(chatClient, properties.getQueryTransform().getMultiQueryCount());
    }

    /** Secondary constructor for tests and manual wiring. */
    public MultiQueryExpanderImpl(ChatClient chatClient, int count) {
        this.chatClient = chatClient;
        this.count = count;
    }

    @Override
    public QueryTransformMode mode() {
        return QueryTransformMode.MULTI_QUERY;
    }

    @Override
    public List<String> transform(String query) {
        try {
            String response = chatClient.prompt()
                    .system(String.format(SYSTEM_TEMPLATE, count))
                    .user(query)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return List.of(query);
            }
            List<String> variants = Arrays.stream(response.split("\\r?\\n"))
                    .map(String::strip)
                    .filter(s -> !s.isBlank())
                    .limit(count)
                    .collect(Collectors.toList());

            // Always include the original to guarantee at least one good search
            if (!variants.contains(query)) {
                variants.add(0, query);
            }
            log.debug("MultiQueryExpand: '{}' -> {} variants", query, variants.size());
            return variants;
        } catch (Exception e) {
            log.warn("MultiQueryExpand failed ({}), using original query", e.getMessage());
            return List.of(query);
        }
    }
}
