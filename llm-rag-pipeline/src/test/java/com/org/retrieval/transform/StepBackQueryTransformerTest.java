package com.org.retrieval.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StepBackQueryTransformerTest {

    private StepBackQueryTransformer transformerReturning(String llmResponse) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(llmResponse);
        return new StepBackQueryTransformer(chatClient);
    }

    @Test
    @DisplayName("Transformer reports its mode as STEP_BACK")
    void modeIsStepBack() {
        assertThat(new StepBackQueryTransformer(mock(ChatClient.class)).mode())
                .isEqualTo(QueryTransformMode.STEP_BACK);
    }

    @Test
    @DisplayName("Returns the LLM's generalised version of a specific query")
    void returnsGeneralisedQuery() {
        StepBackQueryTransformer transformer = transformerReturning(
                "What are chargeback dispute resolution rules for debit card transactions?");

        assertThat(transformer.transform("What is the chargeback time limit for Visa debit in the UK?"))
                .containsExactly("What are chargeback dispute resolution rules for debit card transactions?");
    }

    @Test
    @DisplayName("Strips leading and trailing whitespace from the generalised query")
    void stripsWhitespaceFromGeneralisedQuery() {
        StepBackQueryTransformer transformer = transformerReturning("  general question  ");
        assertThat(transformer.transform("specific question")).containsExactly("general question");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM response is blank")
    void fallsBackToOriginalWhenResponseIsBlank() {
        assertThat(transformerReturning("").transform("original")).containsExactly("original");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM response is null")
    void fallsBackToOriginalWhenResponseIsNull() {
        assertThat(transformerReturning(null).transform("original")).containsExactly("original");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM call throws an exception")
    void fallsBackToOriginalOnLlmException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("model error"));

        assertThat(new StepBackQueryTransformer(chatClient).transform("specific question"))
                .containsExactly("specific question");
    }
}
