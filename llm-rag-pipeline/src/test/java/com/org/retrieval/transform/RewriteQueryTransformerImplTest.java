package com.org.retrieval.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the thin wrapper around Spring AI's {@code RewriteQueryTransformer}, stubbing the
 * {@link ChatClient.Builder} it builds internally.
 */
class RewriteQueryTransformerImplTest {

    private RewriteQueryTransformerImpl transformerReturning(String llmResponse) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.build().prompt().user(any(Consumer.class)).call().content()).thenReturn(llmResponse);
        return new RewriteQueryTransformerImpl(builder);
    }

    @Test
    @DisplayName("Reports REWRITE as its query transform mode")
    void modeIsRewrite() {
        assertThat(transformerReturning(null).mode()).isEqualTo(QueryTransformMode.REWRITE);
    }

    @Test
    @DisplayName("Returns the LLM-rewritten query")
    void returnsRewrittenQuery() {
        RewriteQueryTransformerImpl transformer = transformerReturning("annual leave entitlement policy");
        assertThat(transformer.transform("hey so what's the deal with leave?"))
                .containsExactly("annual leave entitlement policy");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM response is blank")
    void fallsBackToOriginalWhenResponseIsBlank() {
        assertThat(transformerReturning("  ").transform("original")).containsExactly("original");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM response is null")
    void fallsBackToOriginalWhenResponseIsNull() {
        assertThat(transformerReturning(null).transform("original")).containsExactly("original");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM call throws")
    void fallsBackToOriginalOnLlmException() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class, RETURNS_DEEP_STUBS);
        when(builder.build().prompt().user(any(Consumer.class)).call().content())
                .thenThrow(new RuntimeException("service error"));

        assertThat(new RewriteQueryTransformerImpl(builder).transform("original"))
                .containsExactly("original");
    }
}
