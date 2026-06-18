package com.org.retrieval.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests with both sides stubbed — verifies RRF fusion, dedup, and one-side-down resilience.
 */
class HybridSearchStrategyTest {

    private static Document doc(String id, String text) {
        return Document.builder().id(id).text(text).metadata(Map.of("source", "WIKI")).build();
    }

    private static HybridSearchStrategy hybrid(List<Document> dense, List<Document> lexical) {
        VectorSearchStrategy vector = mock(VectorSearchStrategy.class);
        KeywordSearchStrategy keyword = mock(KeywordSearchStrategy.class);
        when(vector.search(anyString(), anyInt())).thenReturn(dense);
        when(keyword.search(anyString(), anyInt())).thenReturn(lexical);
        return new HybridSearchStrategy(vector, keyword);
    }

    @Test
    @DisplayName("A document ranked well by both vector and keyword search wins the RRF fusion")
    void documentRankedWellByBothSidesWinsTheFusion() {
        // "b" is mid-table on both sides; "a" and "c" each top one side but miss the other.
        List<Document> result = hybrid(
                List.of(doc("a", "alpha"), doc("b", "beta")),
                List.of(doc("c", "charlie"), doc("b", "beta")))
                .search("q", 10);

        assertThat(result.get(0).getId()).isEqualTo("b");
        assertThat(result).extracting(Document::getId).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(result.get(0).getScore()).isEqualTo(1.0); // max-normalized
    }

    @Test
    @DisplayName("A document found on both sides is deduplicated and returned only once")
    void duplicatesAcrossSidesAreReturnedOnce() {
        List<Document> result = hybrid(
                List.of(doc("a", "alpha")),
                List.of(doc("a", "alpha")))
                .search("q", 10);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("When one search side throws, results degrade to just the other side")
    void aFailingSideDegradesToTheOtherSide() {
        VectorSearchStrategy vector = mock(VectorSearchStrategy.class);
        KeywordSearchStrategy keyword = mock(KeywordSearchStrategy.class);
        when(vector.search(anyString(), anyInt())).thenThrow(new IllegalStateException("store down"));
        when(keyword.search(anyString(), anyInt())).thenReturn(List.of(doc("a", "alpha")));

        List<Document> result = new HybridSearchStrategy(vector, keyword).search("q", 10);
        assertThat(result).extracting(Document::getId).containsExactly("a");
    }

    @Test
    @DisplayName("Fused results are truncated to the requested topK size")
    void truncatesToTopK() {
        List<Document> result = hybrid(
                List.of(doc("a", "alpha"), doc("b", "beta"), doc("c", "charlie")),
                List.of())
                .search("q", 2);
        assertThat(result).hasSize(2);
    }
}
