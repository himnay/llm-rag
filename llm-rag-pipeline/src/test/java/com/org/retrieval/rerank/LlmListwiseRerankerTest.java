package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests with a stubbed {@link ChatClient} — verifies permutation parsing, not the LLM.
 */
class LlmListwiseRerankerTest {

    private final List<Chunk> chunks = List.of(
            new Chunk("WIKI", "alpha", new HashMap<>(), 0),
            new Chunk("WIKI", "beta", new HashMap<>(), 1),
            new Chunk("WIKI", "gamma", new HashMap<>(), 2));

    private LlmListwiseReranker rerankerReturning(List<Integer> indices) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call()
                .entity(eq(LlmListwiseReranker.RankedOrder.class), any()))
                .thenReturn(indices == null ? null : new LlmListwiseReranker.RankedOrder(indices));
        return new LlmListwiseReranker(chatClient);
    }

    @Test
    @DisplayName("Reorders chunks according to the permutation returned by the LLM")
    void appliesTheReturnedPermutation() {
        List<Chunk> result = rerankerReturning(List.of(2, 0, 1)).rerank("q", chunks);
        assertThat(result).extracting(Chunk::content).containsExactly("gamma", "alpha", "beta");
    }

    @Test
    @DisplayName("Keeps every document even when the LLM reply has duplicate or invalid indices")
    void sloppyReplyNeverLosesDocuments() {
        // Duplicate index, out-of-range index, and a missing index: 'beta' must survive at the tail.
        List<Chunk> result = rerankerReturning(List.of(2, 2, 9, 0)).rerank("q", chunks);
        assertThat(result).extracting(Chunk::content).containsExactly("gamma", "alpha", "beta");
    }

    @Test
    @DisplayName("Keeps the original chunk order when the LLM returns no verdict")
    void noVerdictKeepsOriginalOrder() {
        List<Chunk> result = rerankerReturning(null).rerank("q", chunks);
        assertThat(result).extracting(Chunk::content).containsExactly("alpha", "beta", "gamma");
    }
}
