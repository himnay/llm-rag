package com.org.retrieval.transform;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StepBackQueryTransformerTest {

    private StepBackQueryTransformer transformerReturning(String llmResponse) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(llmResponse);
        return new StepBackQueryTransformer(chatClient);
    }

    @Test
    void modeIsStepBack() {
        assertThat(new StepBackQueryTransformer(mock(ChatClient.class)).mode())
                .isEqualTo(QueryTransformMode.STEP_BACK);
    }

    @Test
    void returnsGeneralisedQuery() {
        StepBackQueryTransformer transformer = transformerReturning(
                "What are chargeback dispute resolution rules for debit card transactions?");

        assertThat(transformer.transform("What is the chargeback time limit for Visa debit in the UK?"))
                .containsExactly("What are chargeback dispute resolution rules for debit card transactions?");
    }

    @Test
    void stripsWhitespaceFromGeneralisedQuery() {
        StepBackQueryTransformer transformer = transformerReturning("  general question  ");
        assertThat(transformer.transform("specific question")).containsExactly("general question");
    }

    @Test
    void fallsBackToOriginalWhenResponseIsBlank() {
        assertThat(transformerReturning("").transform("original")).containsExactly("original");
    }

    @Test
    void fallsBackToOriginalWhenResponseIsNull() {
        assertThat(transformerReturning(null).transform("original")).containsExactly("original");
    }

    @Test
    void fallsBackToOriginalOnLlmException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("model error"));

        assertThat(new StepBackQueryTransformer(chatClient).transform("specific question"))
                .containsExactly("specific question");
    }
}
