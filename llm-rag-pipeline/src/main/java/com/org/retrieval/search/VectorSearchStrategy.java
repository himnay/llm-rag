package com.org.retrieval.search;

import com.org.common.Resilience;
import com.org.retrieval.RetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dense cosine-similarity kNN over the OpenSearch knn index (the classic "R" of RAG, and the
 * default mode). The similarity threshold is pushed down into the vector query so irrelevant hits
 * never leave the store.
 */
@Component
@RequiredArgsConstructor
public class VectorSearchStrategy implements SearchStrategy {

    private final VectorStore vectorStore;
    private final RetrievalProperties properties;

    @Override
    public SearchMode mode() {
        return SearchMode.VECTOR;
    }

    @Override
    public List<Document> search(String query, int topK) {
        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(topK);
        if (properties.getSimilarityThreshold() > 0) {
            builder.similarityThreshold(properties.getSimilarityThreshold());
        }
        SearchRequest searchRequest = builder.build();

        // Embedding + vector search is a network round-trip; retry transient blips before failing.
        return Resilience.withRetry(
                "vector similaritySearch", 3, 200L, () -> vectorStore.similaritySearch(searchRequest));
    }
}
