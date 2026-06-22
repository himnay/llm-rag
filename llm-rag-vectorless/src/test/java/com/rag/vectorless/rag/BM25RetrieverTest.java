package com.rag.vectorless.rag;

import com.rag.vectorless.config.RagProperties;
import com.rag.vectorless.dto.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
                Chunk.builder().text("BM25 is a keyword ranking function used in information retrieval.").source("ir.txt").chunkIndex(0).build(),
                Chunk.builder().text("Spring Boot makes it easy to build standalone Java applications.").source("spring.txt").chunkIndex(0).build(),
                Chunk.builder().text("Vector databases store embeddings for semantic similarity search.").source("vectors.txt").chunkIndex(0).build()
        ));
        retriever = new BM25Retriever(new RagProperties(500, 100, 5, false), loader);
        retriever.buildIndex();
    }

    @Test
    @DisplayName("Ranks the chunk with the most matching keywords first")
    void ranksKeywordMatchingChunkFirst() {
        List<Document> results = retriever.retrieve("How does BM25 keyword ranking work?");

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getText()).contains("BM25");
        assertThat(results.getFirst().getMetadata())
                .containsEntry("source", "ir.txt")
                .containsKey("score");
    }

    @Test
    @DisplayName("Returns an empty result list when no terms match any chunk")
    void returnsEmptyForQueryWithNoMatchingTerms() {
        assertThat(retriever.retrieve("quantum entanglement teleportation")).isEmpty();
    }

    @Test
    @DisplayName("Excludes stop words and short tokens from scoring, yielding no results")
    void stopWordsAndShortTokensDoNotMatch() {
        // every term is a stop word or <= 2 chars, so nothing should score
        assertThat(retriever.retrieve("the and of it is")).isEmpty();
    }
}
