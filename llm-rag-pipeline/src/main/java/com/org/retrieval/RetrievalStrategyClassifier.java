package com.org.retrieval;

import com.org.retrieval.search.SearchMode;
import com.org.retrieval.transform.QueryTransformMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * LLM-based per-query retrieval routing: one call decides both {@link SearchMode} and
 * {@link QueryTransformMode} together, since the two are correlated (e.g. an error-code lookup
 * wants {@code keyword} search and no transform; a vague conversational question wants
 * {@code hybrid}/{@code vector} plus {@code hyde} or {@code rewrite}). Opt-in via
 * {@code app.retrieval.classifier.enabled} — disabled by default.
 *
 * <p>Uses Spring AI's structured-output API to deserialize the LLM's reply directly into a
 * {@link RetrievalStrategy} (both fields are enums, so the generated JSON schema constrains the
 * model to valid values). Falls back to both statically configured defaults
 * ({@code app.retrieval.search.mode}, {@code app.retrieval.query-transform.mode}) together if the
 * call fails or returns nothing — a single structured response is all-or-nothing, unlike the
 * previous free-text/regex approach where each half could be recovered independently.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalStrategyClassifier {

    private static final String PROMPT = """
            Choose the best retrieval strategy for the QUERY below.

            Search mode:
              vector: semantic / paraphrased natural-language questions
              keyword: exact terms — error codes, IDs, names
              hybrid: queries that mix natural language and exact terms

            Query transform:
              none: query is already a clean, specific search query
              rewrite: query is ungrammatical, abbreviated, or conversationally phrased
              multi_query: broad/ambiguous query that could be phrased several ways
              hyde: sparse or technical domain where a hypothetical answer embeds better than the query
              step_back: overly narrow or multi-hop query that needs broader context first

            QUERY: %s
            """;

    private final ChatClient chatClient;
    private final RetrievalProperties properties;

    public RetrievalStrategy classify(String query) {
        SearchMode fallbackSearch = properties.getSearch().getMode();
        QueryTransformMode fallbackTransform = properties.getQueryTransform().getMode();
        try {
            RetrievalStrategy strategy = chatClient.prompt()
                    .user(PROMPT.formatted(query))
                    .call()
                    .entity(RetrievalStrategy.class, spec -> spec.useProviderStructuredOutput());

            if (strategy == null || strategy.searchMode() == null || strategy.transformMode() == null) {
                log.warn("Retrieval strategy classifier returned an incomplete decision; falling back to configured defaults");
                return new RetrievalStrategy(fallbackSearch, fallbackTransform);
            }
            log.debug("RetrievalStrategyClassifier: search={} transform={} | query='{}'",
                    strategy.searchMode(), strategy.transformMode(), query);
            return strategy;
        } catch (Exception e) {
            log.warn("Retrieval strategy classification failed ({}); falling back to configured defaults",
                    e.getMessage());
            return new RetrievalStrategy(fallbackSearch, fallbackTransform);
        }
    }
}
