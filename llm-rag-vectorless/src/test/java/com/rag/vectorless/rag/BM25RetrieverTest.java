package com.rag.vectorless.rag;

import com.rag.vectorless.config.RagProperties;
import com.rag.vectorless.dto.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BM25RetrieverTest {

    private BM25Retriever retriever;

    @BeforeEach
    void setUp() {
        DocumentLoader loader = mock(DocumentLoader.class);
        when(loader.getChunks()).thenReturn(List.of(
                new Chunk("BM25 is a keyword ranking function used in information retrieval.", "ir.txt", 0),
                new Chunk("Spring Boot makes it easy to build standalone Java applications.", "spring.txt", 0),
                new Chunk("Vector databases store embeddings for semantic similarity search.", "vectors.txt", 0)
        ));
        retriever = new BM25Retriever(new RagProperties(500, 100, 5), loader);
        retriever.buildIndex();
    }

    @Test
    void ranksKeywordMatchingChunkFirst() {
        List<Document> results = retriever.retrieve("How does BM25 keyword ranking work?");

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getText()).contains("BM25");
        assertThat(results.getFirst().getMetadata())
                .containsEntry("source", "ir.txt")
                .containsKey("score");
    }

    @Test
    void returnsEmptyForQueryWithNoMatchingTerms() {
        assertThat(retriever.retrieve("quantum entanglement teleportation")).isEmpty();
    }

    @Test
    void stopWordsAndShortTokensDoNotMatch() {
        // every term is a stop word or <= 2 chars, so nothing should score
        assertThat(retriever.retrieve("the and of it is")).isEmpty();
    }
}
