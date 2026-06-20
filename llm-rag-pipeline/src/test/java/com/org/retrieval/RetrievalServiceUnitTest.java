package com.org.retrieval;

import com.org.mongo.ChunkDocumentRepository;
import com.org.retrieval.model.RetrievalResult;
import com.org.retrieval.postprocess.BusinessRuleFilter;
import com.org.retrieval.postprocess.ScoreAwareRanker;
import com.org.retrieval.search.VectorSearchStrategy;
import com.org.retrieval.transform.QueryTransformationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link RetrievalService} wired with a mocked {@link VectorStore} (behind the
 * real vector search strategy) and the real post-processor chain (business-rule visibility filter +
 * score-aware ranker) — no Spring context or infrastructure required.
 */
class RetrievalServiceUnitTest {

    private RetrievalService newService(List<Document> docs) {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(docs);
        RetrievalProperties properties = new RetrievalProperties();
        RetrievalService service = new RetrievalService(properties,
                List.of(new VectorSearchStrategy(vectorStore, properties)),
                List.of(new BusinessRuleFilter(), new ScoreAwareRanker()),
                new QueryTransformationService(List.of()),
                new ChunkHydrationService(mock(ChunkDocumentRepository.class)),
                mock(RetrievalStrategyClassifier.class));
        service.init();
        return service;
    }

    @Test
    @DisplayName("Allows non-database sourced documents through the visibility filter unconditionally")
    void nonDbSourcesAreAlwaysAllowed() {
        RetrievalResult result = newService(List.of(
                new Document("wiki content", Map.of("source", "WIKI", "chunkIndex", 0))
        )).retrieve("anything");
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.citations()).hasSize(1);
    }

    @Test
    @DisplayName("Filters out restricted-visibility FAQ rows while keeping public ones")
    void restrictedFaqIsFilteredOut() {
        RetrievalResult result = newService(List.of(
                new Document("public", Map.of("source", "DB", "table", "faqs", "visibility", "PUBLIC", "chunkIndex", 0)),
                new Document("secret", Map.of("source", "DB", "table", "faqs", "visibility", "RESTRICTED", "chunkIndex", 1))
        )).retrieve("q");
        assertThat(result.chunks()).hasSize(1);
        assertThat(result.chunks().get(0).content()).isEqualTo("public");
    }

    @Test
    @DisplayName("Tolerates documents with missing metadata keys without throwing")
    void missingMetadataKeysDoNotThrow() {
        // DB announcement with no effective dates, and a doc missing 'source' entirely
        RetrievalResult result = newService(List.of(
                new Document("ann", Map.of("source", "DB", "table", "announcements", "chunkIndex", 0)),
                new Document("orphan", Map.of("chunkIndex", 1))
        )).retrieve("q");
        assertThat(result.chunks()).hasSize(2);
    }
}
