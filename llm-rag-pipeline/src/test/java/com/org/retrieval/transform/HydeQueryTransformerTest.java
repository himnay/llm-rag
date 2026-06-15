package com.org.retrieval.transform;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HydeQueryTransformerTest {

    private HydeQueryTransformer transformerReturning(String llmResponse) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(llmResponse);
        return new HydeQueryTransformer(chatClient);
    }

    @Test
    void modeIsHyde() {
        assertThat(new HydeQueryTransformer(mock(ChatClient.class)).mode())
                .isEqualTo(QueryTransformMode.HYDE);
    }

    @Test
    void returnsHypotheticalPassageFromLlm() {
        HydeQueryTransformer transformer = transformerReturning("Employees are entitled to 25 days of annual leave.");
        assertThat(transformer.transform("What is the leave policy?"))
                .containsExactly("Employees are entitled to 25 days of annual leave.");
    }

    @Test
    void stripsLeadingTrailingWhitespaceFromResponse() {
        HydeQueryTransformer transformer = transformerReturning("  hypothetical answer  ");
        assertThat(transformer.transform("query")).containsExactly("hypothetical answer");
    }

    @Test
    void fallsBackToOriginalWhenResponseIsBlank() {
        HydeQueryTransformer transformer = transformerReturning("   ");
        assertThat(transformer.transform("my query")).containsExactly("my query");
    }

    @Test
    void fallsBackToOriginalWhenResponseIsNull() {
        HydeQueryTransformer transformer = transformerReturning(null);
        assertThat(transformer.transform("my query")).containsExactly("my query");
    }

    @Test
    void fallsBackToOriginalOnLlmException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("LLM unavailable"));

        assertThat(new HydeQueryTransformer(chatClient).transform("my query"))
                .containsExactly("my query");
    }
}
