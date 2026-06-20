package com.org.chunking.strategy;

import com.org.ingestion.model.IngestedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkingStrategyClassifierTest {

    private final ChunkingProperties properties = new ChunkingProperties();

    private ChunkingStrategyClassifier classifierReturning(ChunkingStrategyClassifier.ChunkingChoice choice) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call()
                .entity(eq(ChunkingStrategyClassifier.StrategyDecision.class), any()))
                .thenReturn(choice == null ? null : new ChunkingStrategyClassifier.StrategyDecision(choice));
        return new ChunkingStrategyClassifier(chatClient, properties);
    }

    private static IngestedDocument document() {
        return new IngestedDocument("PDF", "some document content", Map.of());
    }

    @Test
    @DisplayName("Returns the strategy name parsed from a valid decision")
    void validDecisionReturnsStrategy() {
        Optional<String> result = classifierReturning(ChunkingStrategyClassifier.ChunkingChoice.RECURSIVE)
                .classify(document());
        assertThat(result).contains("recursive");
    }

    @Test
    @DisplayName("Returns empty when the LLM returns no decision")
    void noDecisionReturnsEmpty() {
        Optional<String> result = classifierReturning(null).classify(document());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Returns empty when the LLM call throws")
    void exceptionDuringClassificationReturnsEmpty() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call()
                .entity(eq(ChunkingStrategyClassifier.StrategyDecision.class), any()))
                .thenThrow(new RuntimeException("boom"));

        Optional<String> result = new ChunkingStrategyClassifier(chatClient, properties).classify(document());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Truncates the document excerpt sent to the LLM to the configured sample size")
    void truncatesExcerptToSampleChars() {
        properties.getClassifier().setSampleChars(10);
        AtomicReference<String> promptSeen = new AtomicReference<>();
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString())).thenAnswer(invocation -> {
            promptSeen.set(invocation.getArgument(0));
            return chatClient.prompt();
        });
        when(chatClient.prompt().call().entity(eq(ChunkingStrategyClassifier.StrategyDecision.class), any()))
                .thenReturn(new ChunkingStrategyClassifier.StrategyDecision(
                        ChunkingStrategyClassifier.ChunkingChoice.TOKEN));

        new ChunkingStrategyClassifier(chatClient, properties)
                .classify(new IngestedDocument("PDF", "0123456789ABCDEFGHIJ", Map.of()));

        assertThat(promptSeen.get()).contains("0123456789").doesNotContain("ABCDEFGHIJ");
    }
}
