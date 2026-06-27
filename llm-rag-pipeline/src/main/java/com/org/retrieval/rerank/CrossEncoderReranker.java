package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pointwise neural cross-encoder reranking via an external Cohere-compatible {@code /v1/rerank}
 * API. The cross-encoder reads query and document <em>together</em>, so it captures interactions a
 * bi-encoder misses — the strongest (and priciest) reranking signal. Requires
 * {@code app.retrieval.rerank.api-key}; without one the candidates pass through unchanged.
 */
@Slf4j
@Component
public class CrossEncoderReranker implements Reranker {

    private final RetrievalProperties properties;
    private final RestClient restClient;

    /**
     * Builds the REST client used to call the Cohere-compatible rerank API, with a bounded read
     * timeout from {@code app.retrieval.rerank.timeout}.
     */
    public CrossEncoderReranker(RetrievalProperties properties) {
        this.properties = properties;
        // Connect + read timeouts so a slow/unavailable rerank vendor degrades to pass-through.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRerank().getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getRerank().getTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getRerank().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.CROSS_ENCODER;
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        RetrievalProperties.Rerank cfg = properties.getRerank();
        if (cfg.getApiKey().isBlank()) {
            log.warn("Cross-encoder rerank enabled but no API key configured — skipping");
            return chunks;
        }
        List<String> documents = chunks.stream().map(Chunk::content).toList();

        RerankResponse response = restClient.post()
                .uri("/v1/rerank")
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .body(Map.of(
                        "model", cfg.getModel(),
                        "query", query,
                        "documents", documents,
                        "top_n", documents.size()))
                .retrieve()
                .body(RerankResponse.class);

        if (response == null || response.results() == null || response.results().isEmpty()) {
            log.warn("Cross-encoder returned no results — passing candidates through unchanged");
            return chunks;
        }

        List<Chunk> reranked = new ArrayList<>(chunks.size());
        for (RerankResult result : response.results()) {
            if (result.index() < 0 || result.index() >= chunks.size()) {
                continue;
            }
            Chunk chunk = chunks.get(result.index());
            Reranker.score(chunk, result.relevanceScore());
            reranked.add(chunk);
        }
        return reranked.isEmpty() ? chunks : reranked;
    }

    // Cohere /v1/rerank response shape (only the fields we use).
    record RerankResponse(List<RerankResult> results) {
    }

    record RerankResult(int index,
                        @com.fasterxml.jackson.annotation.JsonProperty("relevance_score") double relevanceScore) {
    }
}
