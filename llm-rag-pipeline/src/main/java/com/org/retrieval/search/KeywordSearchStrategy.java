package com.org.retrieval.search;

import com.org.common.Resilience;
import com.org.config.OpenSearchProperties;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lexical BM25 full-text search on the {@code content} field of the same OpenSearch index the
 * vectors live in — no embedding call, and exact terms (error codes, names, IDs) match directly.
 * Raw BM25 scores are unbounded, so they are max-normalized to 0..1 to stay comparable with the
 * cosine scores the rest of the pipeline expects.
 */
@Slf4j
@Component
public class KeywordSearchStrategy implements SearchStrategy {

    private final OpenSearchClient client;
    private final String indexName;

    public KeywordSearchStrategy(OpenSearchClient client, OpenSearchProperties openSearchProperties) {
        this.client = client;
        this.indexName = openSearchProperties.getIndexName();
    }

    @Override
    public SearchMode mode() {
        return SearchMode.KEYWORD;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Document> search(String query, int topK) {
        SearchResponse<Map> response = Resilience.withRetry("keyword match search", 3, 200L, () -> {
            try {
                return client.search(s -> s
                                .index(indexName)
                                .size(topK)
                                .query(q -> q.match(m -> m.field("content").query(v -> v.stringValue(query)))),
                        Map.class);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        List<Hit<Map>> hits = response.hits().hits();
        double maxScore = hits.stream().mapToDouble(h -> h.score() == null ? 0 : h.score()).max().orElse(0);

        List<Document> documents = new ArrayList<>(hits.size());
        for (Hit<Map> hit : hits) {
            Map<String, Object> source = hit.source();
            if (source == null) {
                continue;
            }
            Object content = source.get("content");
            Map<String, Object> metadata = source.get("metadata") instanceof Map m
                    ? new HashMap<String, Object>(m) : new HashMap<String, Object>();
            double score = (hit.score() == null || maxScore == 0) ? 0.0 : hit.score() / maxScore;
            documents.add(Document.builder()
                    .id(hit.id())
                    .text(content == null ? "" : content.toString())
                    .metadata(metadata)
                    .score(score)
                    .build());
        }
        return documents;
    }
}
