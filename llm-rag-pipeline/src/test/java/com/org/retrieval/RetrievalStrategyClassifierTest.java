package com.org.retrieval;

import com.org.retrieval.search.SearchMode;
import com.org.retrieval.transform.QueryTransformMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalStrategyClassifierTest {

    private final RetrievalProperties properties = new RetrievalProperties();

    private RetrievalStrategyClassifier classifierReturning(RetrievalStrategy strategy) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().entity(eq(RetrievalStrategy.class), any()))
                .thenReturn(strategy);
        return new RetrievalStrategyClassifier(chatClient, properties);
    }

    @Test
    @DisplayName("Returns the strategy decided by the LLM")
    void validDecisionIsReturned() {
        RetrievalStrategy result = classifierReturning(
                new RetrievalStrategy(SearchMode.KEYWORD, QueryTransformMode.REWRITE)).classify("error code E1234");

        assertThat(result.searchMode()).isEqualTo(SearchMode.KEYWORD);
        assertThat(result.transformMode()).isEqualTo(QueryTransformMode.REWRITE);
    }

    @Test
    @DisplayName("Falls back to both configured defaults when the LLM returns no decision")
    void fallsBackToBothDefaultsOnNullDecision() {
        RetrievalStrategy result = classifierReturning(null).classify("q");

        assertThat(result.searchMode()).isEqualTo(properties.getSearch().getMode());
        assertThat(result.transformMode()).isEqualTo(properties.getQueryTransform().getMode());
    }

    @Test
    @DisplayName("Falls back to both configured defaults when the decision is incomplete")
    void fallsBackToBothDefaultsOnIncompleteDecision() {
        RetrievalStrategy result = classifierReturning(new RetrievalStrategy(SearchMode.HYBRID, null)).classify("q");

        assertThat(result.searchMode()).isEqualTo(properties.getSearch().getMode());
        assertThat(result.transformMode()).isEqualTo(properties.getQueryTransform().getMode());
    }

    @Test
    @DisplayName("Falls back to both configured defaults when the LLM call throws")
    void fallsBackToBothDefaultsOnException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().entity(eq(RetrievalStrategy.class), any()))
                .thenThrow(new RuntimeException("boom"));

        RetrievalStrategy result = new RetrievalStrategyClassifier(chatClient, properties).classify("q");

        assertThat(result.searchMode()).isEqualTo(properties.getSearch().getMode());
        assertThat(result.transformMode()).isEqualTo(properties.getQueryTransform().getMode());
    }
}
