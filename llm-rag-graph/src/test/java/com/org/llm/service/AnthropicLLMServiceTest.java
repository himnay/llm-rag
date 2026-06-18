package com.org.llm.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnthropicLLMService} failure scenarios —
 * verifies that SDK/network exceptions are caught and re-thrown as {@link LlmCallException}
 * rather than propagating as raw runtime errors with stack traces.
 */
@ExtendWith(MockitoExtension.class)
class AnthropicLLMServiceTest {

    @Mock
    private AnthropicClient anthropicClient;
    @Mock
    private MessageService messageService;

    private AnthropicLLMService service;

    @BeforeEach
    void setUp() {
        service = new AnthropicLLMService(anthropicClient);
        ReflectionTestUtils.setField(service, "model", "claude-opus-4-8");
        ReflectionTestUtils.setField(service, "maxTokens", 4096);
    }

    @DisplayName("Network failure during message creation is wrapped as LlmCallException")
    @Test
    void networkFailureWrappedAsLlmCallException() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.answer("What teams exist?", "graph context"))
                .isInstanceOf(LlmCallException.class)
                .hasMessageContaining("Connection refused")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @DisplayName("Authentication failure during message creation is wrapped as LlmCallException")
    @Test
    void authFailureWrappedAsLlmCallException() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenThrow(new IllegalStateException("401 Unauthorized"));

        assertThatThrownBy(() -> service.answer("Who is Alice?", "context"))
                .isInstanceOf(LlmCallException.class)
                .hasMessageContaining("401 Unauthorized");
    }

    @DisplayName("Groundedness check defaults to true when the LLM call fails")
    @Test
    void groundednessCheckDefaultsToTrueWhenLlmCallFails() {
        when(anthropicClient.messages()).thenReturn(messageService);
        when(messageService.create(any(MessageCreateParams.class))).thenThrow(new RuntimeException("rate limited"));

        boolean grounded = service.checkGroundedness("graph context", "some answer");

        assertThat(grounded).isTrue();
    }
}
