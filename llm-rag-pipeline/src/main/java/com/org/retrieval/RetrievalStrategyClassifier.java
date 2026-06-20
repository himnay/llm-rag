package com.org.retrieval;

import com.org.retrieval.search.SearchMode;
import com.org.retrieval.transform.QueryTransformMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-based per-query retrieval routing: one call decides both {@link SearchMode} and
 * {@link QueryTransformMode} together, since the two are correlated (e.g. an error-code lookup
 * wants {@code keyword} search and no transform; a vague conversational question wants
 * {@code hybrid}/{@code vector} plus {@code hyde} or {@code rewrite}). Opt-in via
 * {@code app.retrieval.classifier.enabled} — disabled by default.
 *
 * <p>Each half of the decision falls back independently to the statically configured default
 * ({@code app.retrieval.search.mode}, {@code app.retrieval.query-transform.mode}) if the LLM call
 * fails or that half of the reply is unparseable, so a malformed response only loses the part that
 * couldn't be read rather than the whole classification.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalStrategyClassifier {

    private static final Pattern SEARCH_MODE = Pattern.compile("vector|keyword|hybrid");
    private static final Pattern TRANSFORM_MODE = Pattern.compile("multi_query|step_back|rewrite|hyde|none");

    private static final String PROMPT = """
            Choose the best retrieval strategy for the QUERY below.

            Search mode — one of: vector, keyword, hybrid
              vector: semantic / paraphrased natural-language questions
              keyword: exact terms — error codes, IDs, names
              hybrid: queries that mix natural language and exact terms

            Query transform — one of: none, rewrite, multi_query, hyde, step_back
              none: query is already a clean, specific search query
              rewrite: query is ungrammatical, abbreviated, or conversationally phrased
              multi_query: broad/ambiguous query that could be phrased several ways
              hyde: sparse or technical domain where a hypothetical answer embeds better than the query
              step_back: overly narrow or multi-hop query that needs broader context first

            Respond with ONLY: <search_mode>,<query_transform>

            QUERY: %s
            """;

    private final ChatClient chatClient;
    private final RetrievalProperties properties;

    public RetrievalStrategy classify(String query) {
        SearchMode fallbackSearch = properties.getSearch().getMode();
        QueryTransformMode fallbackTransform = properties.getQueryTransform().getMode();
        try {
            String reply = chatClient.prompt().user(PROMPT.formatted(query)).call().content();
            String normalized = reply == null ? "" : reply.toLowerCase(Locale.ROOT);

            SearchMode searchMode = extract(SEARCH_MODE, normalized)
                    .map(s -> SearchMode.valueOf(s.toUpperCase(Locale.ROOT)))
                    .orElse(fallbackSearch);
            QueryTransformMode transformMode = extract(TRANSFORM_MODE, normalized)
                    .map(s -> QueryTransformMode.valueOf(s.toUpperCase(Locale.ROOT)))
                    .orElse(fallbackTransform);

            log.debug("RetrievalStrategyClassifier: search={} transform={} | query='{}'",
                    searchMode, transformMode, query);
            return new RetrievalStrategy(searchMode, transformMode);
        } catch (Exception e) {
            log.warn("Retrieval strategy classification failed ({}); falling back to configured defaults",
                    e.getMessage());
            return new RetrievalStrategy(fallbackSearch, fallbackTransform);
        }
    }

    private static Optional<String> extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }
}
