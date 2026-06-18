package com.org.retrieval.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RewriteQueryTransformerImplTest {

    private RewriteQueryTransformerImpl transformerReturning(String llmResponse) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(llmResponse);
        return new RewriteQueryTransformerImpl(chatClient);
    }

    @Test
    @DisplayName("Reports REWRITE as its query transform mode")
    void modeIsRewrite() {
        assertThat(new RewriteQueryTransformerImpl(mock(ChatClient.class)).mode())
                .isEqualTo(QueryTransformMode.REWRITE);
    }

    @Test
    @DisplayName("Returns the LLM-rewritten query")
    void returnsRewrittenQuery() {
        RewriteQueryTransformerImpl transformer = transformerReturning("annual leave entitlement policy");
        assertThat(transformer.transform("hey so what's the deal with leave?"))
                .containsExactly("annual leave entitlement policy");
    }

    @Test
    @DisplayName("Strips leading and trailing whitespace from the rewritten query")
    void stripsWhitespaceFromRewrittenQuery() {
        RewriteQueryTransformerImpl transformer = transformerReturning("  clean query  ");
        assertThat(transformer.transform("messy question")).containsExactly("clean query");
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
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("service error"));

        assertThat(new RewriteQueryTransformerImpl(chatClient).transform("original"))
                .containsExactly("original");
    }
}
