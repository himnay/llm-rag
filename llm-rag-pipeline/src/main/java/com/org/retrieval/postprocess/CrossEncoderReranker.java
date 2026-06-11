package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Optional second-stage reranking via an external cross-encoder (Cohere-compatible {@code /v1/rerank}).
 * When enabled and an API key is present, it re-scores the candidates and writes the relevance into
 * each chunk's {@code score} metadata (so {@link ScoreAwareRanker} then orders by it). Any failure
 * degrades gracefully — the candidates pass through unchanged. Disabled by default.
 */
@Slf4j
@Component
public class CrossEncoderReranker implements RetrievalPostProcessor {

    private final RetrievalProperties properties;
    private final RestClient restClient;

    public CrossEncoderReranker(RetrievalProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getRerank().getBaseUrl())
                .build();
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public List<Chunk> process(String query, List<Chunk> chunks) {
        RetrievalProperties.Rerank cfg = properties.getRerank();
        if (!cfg.isEnabled() || cfg.getApiKey().isBlank() || chunks.size() < 2) {
            return chunks;
        }
        try {
            List<String> documents = chunks.stream().map(Chunk::content).toList();
            int topN = cfg.getTopN() > 0 ? Math.min(cfg.getTopN(), chunks.size()) : chunks.size();

            RerankResponse response = restClient.post()
                    .uri("/v1/rerank")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .body(Map.of(
                            "model", cfg.getModel(),
                            "query", query,
                            "documents", documents,
                            "top_n", topN))
                    .retrieve()
                    .body(RerankResponse.class);

            if (response == null || response.results() == null || response.results().isEmpty()) {
                log.warn("Reranker returned no results — passing candidates through unchanged");
                return chunks;
            }

            List<Chunk> reranked = new ArrayList<>(response.results().size());
            for (RerankResult result : response.results()) {
                if (result.index() < 0 || result.index() >= chunks.size()) {
                    continue;
                }
                Chunk chunk = chunks.get(result.index());
                chunk.metadata().put(SCORE_KEY, result.relevanceScore());
                reranked.add(chunk);
            }
            return reranked.isEmpty() ? chunks : reranked;
        } catch (Exception e) {
            log.warn("Reranker call failed ({}); passing candidates through unchanged", e.getMessage());
            return chunks;
        }
    }

    // Cohere /v1/rerank response shape (only the fields we use).
    record RerankResponse(List<RerankResult> results) {
    }

    record RerankResult(int index,
                        @com.fasterxml.jackson.annotation.JsonProperty("relevance_score") double relevanceScore) {
    }
}
