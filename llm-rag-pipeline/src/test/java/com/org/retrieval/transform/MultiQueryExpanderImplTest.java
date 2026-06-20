package com.org.retrieval.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the thin wrapper around Spring AI's {@code MultiQueryExpander}, stubbing the
 * {@link ChatClient.Builder} it builds internally. Spring AI's expander requires the LLM reply to
 * split into <em>exactly</em> {@code count} lines or it falls back to the original query only —
 * stricter than the old hand-rolled implementation, which tolerated blank lines and a wrong count.
 */
class MultiQueryExpanderImplTest {

    private MultiQueryExpanderImpl expanderReturning(String llmResponse, int count) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.build().prompt().user(any(Consumer.class)).call().content()).thenReturn(llmResponse);
        return new MultiQueryExpanderImpl(builder, count);
    }

    @Test
    @DisplayName("Reports MULTI_QUERY as its query transform mode")
    void modeIsMultiQuery() {
        assertThat(expanderReturning(null, 3).mode()).isEqualTo(QueryTransformMode.MULTI_QUERY);
    }

    @Test
    @DisplayName("Returns LLM-generated query variants and always includes the original query")
    void returnsVariantsAndAlwaysIncludesOriginal() {
        // Exactly 3 lines, matching count=3, as Spring AI's expander requires.
        MultiQueryExpanderImpl expander = expanderReturning(
                "Annual leave entitlement rules?\nHow many days off per year?\nVacation policy details?", 3);
        List<String> result = expander.transform("leave policy");

        assertThat(result.get(0)).isEqualTo("leave policy");
        assertThat(result).hasSize(4); // original + 3 variants
    }

    @Test
    @DisplayName("Falls back to the original query only when the reply has the wrong number of lines")
    void fallsBackToOriginalOnWrongLineCount() {
        // count=2 but the LLM returned 5 lines — Spring AI's expander requires an exact match.
        MultiQueryExpanderImpl expander = expanderReturning("A\nB\nC\nD\nE", 2);
        assertThat(expander.transform("original")).containsExactly("original");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM response is null")
    void fallsBackToOriginalOnNullResponse() {
        MultiQueryExpanderImpl expander = expanderReturning(null, 3);
        assertThat(expander.transform("my query")).containsExactly("my query");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM call throws")
    void fallsBackToOriginalOnLlmException() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.build().prompt().user(any(Consumer.class)).call().content())
                .thenThrow(new RuntimeException("timeout"));

        assertThat(new MultiQueryExpanderImpl(builder, 3).transform("q")).containsExactly("q");
    }
}
