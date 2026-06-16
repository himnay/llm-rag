package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests with a stubbed {@link ChatClient} — verifies permutation parsing, not the LLM.
 */
class LlmListwiseRerankerTest {

    private final List<Chunk> chunks = List.of(
            new Chunk("WIKI", "alpha", new HashMap<>(), 0),
            new Chunk("WIKI", "beta", new HashMap<>(), 1),
            new Chunk("WIKI", "gamma", new HashMap<>(), 2));

    private LlmListwiseReranker rerankerReplying(String reply) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(reply);
        return new LlmListwiseReranker(chatClient);
    }

    @Test
    void appliesTheReturnedPermutation() {
        List<Chunk> result = rerankerReplying("2, 0, 1").rerank("q", chunks);
        assertThat(result).extracting(Chunk::content).containsExactly("gamma", "alpha", "beta");
    }

    @Test
    void sloppyReplyNeverLosesDocuments() {
        // Duplicate index, out-of-range index, and a missing index: 'beta' must survive at the tail.
        List<Chunk> result = rerankerReplying("2, 2, 9, 0").rerank("q", chunks);
        assertThat(result).extracting(Chunk::content).containsExactly("gamma", "alpha", "beta");
    }

    @Test
    void unparseableReplyKeepsOriginalOrder() {
        List<Chunk> result = rerankerReplying("sorry, I cannot rank these").rerank("q", chunks);
        assertThat(result).extracting(Chunk::content).containsExactly("alpha", "beta", "gamma");
    }
}
