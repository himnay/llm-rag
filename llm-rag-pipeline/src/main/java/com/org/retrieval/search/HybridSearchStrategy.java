package com.org.retrieval.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid search: runs {@link VectorSearchStrategy dense} and {@link KeywordSearchStrategy lexical}
 * search and fuses the two rankings with Reciprocal Rank Fusion ({@code Σ 1/(k + rank)}), deduped
 * by document id. Rank fusion sidesteps comparing cosine and BM25 scores directly, and makes
 * retrieval robust for queries that mix natural language with exact terms ("how do I fix E1234").
 * Composes the other two strategies (GoF Composite over Strategy) — if one side fails, the other
 * side's results are used alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HybridSearchStrategy implements SearchStrategy {

    /**
     * Standard RRF damping constant from the original Cormack et al. paper.
     */
    private static final int K = 60;

    private final VectorSearchStrategy vectorSearch;
    private final KeywordSearchStrategy keywordSearch;

    private static void accumulate(List<Document> side, Map<String, Document> byId, Map<String, Double> fused) {
        for (int rank = 0; rank < side.size(); rank++) {
            Document document = side.get(rank);
            String key = document.getId() == null || document.getId().isBlank()
                    ? document.getText() : document.getId();
            byId.putIfAbsent(key, document);
            fused.merge(key, 1.0 / (K + rank + 1), Double::sum);
        }
    }

    /**
     * One side of the hybrid; a failing side contributes nothing instead of failing the query.
     */
    private static List<Document> sideOf(String name, java.util.function.Supplier<List<Document>> side) {
        try {
            return side.get();
        } catch (Exception e) {
            log.warn("Hybrid search: {} side failed ({}); continuing with the other side", name, e.getMessage());
            return List.of();
        }
    }

    @Override
    public SearchMode mode() {
        return SearchMode.HYBRID;
    }

    @Override
    public List<Document> search(String query, int topK) {
        List<Document> dense = sideOf("vector", () -> vectorSearch.search(query, topK));
        List<Document> lexical = sideOf("keyword", () -> keywordSearch.search(query, topK));

        Map<String, Document> byId = new LinkedHashMap<>();
        Map<String, Double> fused = new LinkedHashMap<>();
        accumulate(dense, byId, fused);
        accumulate(lexical, byId, fused);

        double max = fused.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        List<Map.Entry<String, Double>> ranked = new ArrayList<>(fused.entrySet());
        ranked.sort(Map.Entry.<String, Double>comparingByValue().reversed());

        List<Document> result = new ArrayList<>(Math.min(topK, ranked.size()));
        for (Map.Entry<String, Double> entry : ranked.subList(0, Math.min(topK, ranked.size()))) {
            Document document = byId.get(entry.getKey());
            result.add(document.mutate().score(max == 0 ? 0.0 : entry.getValue() / max).build());
        }
        return result;
    }
}
