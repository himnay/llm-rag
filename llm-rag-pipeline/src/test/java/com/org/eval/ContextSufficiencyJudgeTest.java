package com.org.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextSufficiencyJudgeTest {

    private ContextSufficiencyJudge judgeReturning(Boolean sufficient) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call()
                .entity(eq(ContextSufficiencyJudge.SufficiencyVerdict.class), any()))
                .thenReturn(sufficient == null ? null
                        : new ContextSufficiencyJudge.SufficiencyVerdict(sufficient, "because"));
        return new ContextSufficiencyJudge(chatClient);
    }

    @Test
    @DisplayName("sufficient=true verdict is judged sufficient")
    void sufficientVerdictIsSufficient() {
        assertThat(judgeReturning(true).isSufficient("q", "some context")).isTrue();
    }

    @Test
    @DisplayName("sufficient=false verdict is judged insufficient")
    void insufficientVerdictIsInsufficient() {
        assertThat(judgeReturning(false).isSufficient("q", "some context")).isFalse();
    }

    @Test
    @DisplayName("Blank context is judged insufficient without calling the LLM")
    void blankContextSkipsLlmCall() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        ContextSufficiencyJudge judge = new ContextSufficiencyJudge(chatClient);

        assertThat(judge.isSufficient("q", "")).isFalse();
        assertThat(judge.isSufficient("q", null)).isFalse();
        verify(chatClient, never()).prompt();
    }

    @Test
    @DisplayName("Defaults to sufficient (fail-open) when the LLM returns no verdict")
    void noVerdictFailsOpen() {
        assertThat(judgeReturning(null).isSufficient("q", "some context")).isTrue();
    }

    @Test
    @DisplayName("Defaults to sufficient (fail-open) when the LLM call throws")
    void exceptionDuringJudgmentFailsOpen() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call()
                .entity(eq(ContextSufficiencyJudge.SufficiencyVerdict.class), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(new ContextSufficiencyJudge(chatClient).isSufficient("q", "some context")).isTrue();
    }
}
