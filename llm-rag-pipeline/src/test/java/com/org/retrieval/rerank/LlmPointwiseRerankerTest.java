package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests with a stubbed {@link ChatClient} whose grade depends on the prompted document.
 */
class LlmPointwiseRerankerTest {

    /**
     * A ChatClient whose reply is computed from the user prompt (thread-safe for parallel calls).
     */
    private static ChatClient replying(Function<String, String> replyForPrompt) {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenAnswer(promptInv -> {
            ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
            when(spec.user(anyString())).thenAnswer(userInv -> {
                ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
                when(call.content()).thenReturn(replyForPrompt.apply(userInv.getArgument(0)));
                when(spec.call()).thenReturn(call);
                return spec;
            });
            return spec;
        });
        return chatClient;
    }

    @Test
    @DisplayName("Grades chunks in parallel via the LLM and orders results by grade")
    void gradesInParallelAndOrdersByGrade() {
        List<Chunk> chunks = List.of(
                new Chunk("WIKI", "company picnic announcement", new HashMap<>(), 0),
                new Chunk("WIKI", "vpn setup guide", new HashMap<>(), 1));
        // Key on text unique to the document — the query itself appears in every prompt.
        ChatClient chatClient = replying(prompt -> prompt.contains("setup guide") ? "95" : "10");

        List<Chunk> result = new LlmPointwiseReranker(chatClient).rerank("how to set up vpn", chunks);

        assertThat(result.get(0).content()).isEqualTo("vpn setup guide");
        assertThat(RetrievalPostProcessor.score(result.get(0))).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Scores a chunk zero instead of failing when the LLM grade is unparseable")
    void unparseableGradeScoresZeroInsteadOfFailing() {
        List<Chunk> chunks = List.of(
                new Chunk("WIKI", "alpha", new HashMap<>(), 0),
                new Chunk("WIKI", "beta", new HashMap<>(), 1));
        List<Chunk> result = new LlmPointwiseReranker(replying(p -> "no idea")).rerank("q", chunks);
        assertThat(result).hasSize(2);
        assertThat(RetrievalPostProcessor.score(result.get(0))).isZero();
    }
}
