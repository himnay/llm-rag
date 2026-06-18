package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test with a stubbed {@link EmbeddingModel} — no embedding service required.
 */
class BiEncoderRerankerTest {

    @Test
    @DisplayName("Reorders chunks by exact cosine similarity between embeddings")
    void reordersByExactCosineSimilarity() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1f, 0f});
        when(embeddingModel.embed(anyList())).thenReturn(List.of(
                new float[]{0f, 1f},      // orthogonal to the query → last
                new float[]{1f, 0.1f}));  // near-parallel → first

        List<Chunk> chunks = List.of(
                new Chunk("WIKI", "off-topic", new HashMap<>(), 0),
                new Chunk("WIKI", "on-topic", new HashMap<>(), 1));

        List<Chunk> result = new BiEncoderReranker(embeddingModel).rerank("q", chunks);

        assertThat(result.get(0).content()).isEqualTo("on-topic");
        assertThat(result.get(1).content()).isEqualTo("off-topic");
    }
}
