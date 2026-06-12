package com.org.retrieval;

import com.org.chunking.model.Chunk;
import com.org.retrieval.model.Citation;
import com.org.retrieval.model.RetrievalResult;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import com.org.retrieval.search.SearchMode;
import com.org.retrieval.search.SearchStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Retrieval API — the "R" of this RAG service. Over-fetches candidates via the configured
 * {@link SearchStrategy} (vector kNN, keyword BM25, or hybrid RRF — {@code app.retrieval.search.mode}),
 * runs them through the ordered {@link RetrievalPostProcessor} chain (business rules → length →
 * dedup → rerank → score-aware ranking → MMR), trims to {@code topK}, and attaches
 * {@link Citation}s for provenance.
 */
@Slf4j
@Service
public class RetrievalService {

    private final RetrievalProperties properties;
    private final Map<SearchMode, SearchStrategy> searchStrategies = new EnumMap<>(SearchMode.class);
    private final List<RetrievalPostProcessor> postProcessors;

    public RetrievalService(RetrievalProperties properties, List<SearchStrategy> searchStrategies,
                            List<RetrievalPostProcessor> postProcessors) {
        this.properties = properties;
        searchStrategies.forEach(s -> this.searchStrategies.put(s.mode(), s));
        this.postProcessors = postProcessors.stream()
                .sorted(Comparator.comparingInt(RetrievalPostProcessor::getOrder))
                .toList();
    }

    /** Retrieve using the configured default {@code topK}. */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, properties.getDefaultTopK());
    }

    /** Retrieve the top-{@code topK} chunks for a query, post-processed and ranked, with citations. */
    public RetrievalResult retrieve(String query, int topK) {
        int k = topK > 0 ? topK : properties.getDefaultTopK();
        int fetch = k * Math.max(1, properties.getOverFetchFactor());
        SearchMode mode = properties.getSearch().getMode();
        log.info("Retrieval requested | query='{}' | topK={} | fetch={} | mode={}", query, k, fetch, mode);

        SearchStrategy strategy = searchStrategies.get(mode);
        if (strategy == null) {
            throw new IllegalStateException("No search strategy registered for mode " + mode);
        }
        List<Document> documents = strategy.search(query, fetch);
        log.info("{} search returned {} candidate document(s)", mode, documents.size());

        List<Chunk> chunks = documents.stream().map(this::toChunk).collect(Collectors.toList());
        for (RetrievalPostProcessor processor : postProcessors) {
            chunks = processor.process(query, chunks);
        }
        if (chunks.size() > k) {
            chunks = new ArrayList<>(chunks.subList(0, k));
        }
        return new RetrievalResult(chunks, toCitations(chunks));
    }

    private Chunk toChunk(Document document) {
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        if (document.getScore() != null) {
            metadata.put(RetrievalPostProcessor.SCORE_KEY, document.getScore());
        }
        return new Chunk(str(metadata.get("source")), document.getText(),
                metadata, parseInt(metadata.get("chunkIndex")));
    }

    /** One citation per distinct (identity, page, chunk), preserving rank order. */
    private List<Citation> toCitations(List<Chunk> chunks) {
        LinkedHashMap<String, Citation> seen = new LinkedHashMap<>();
        for (Chunk chunk : chunks) {
            Map<String, Object> m = chunk.metadata();
            String identity = str(m.get("identity"));
            Integer page = pageOf(m);
            String key = identity + "#" + page + "#" + chunk.chunkIndex();
            seen.putIfAbsent(key, new Citation(
                    chunk.source(), str(m.get("fileName")),
                    identity.isEmpty() ? null : identity,
                    page, chunk.chunkIndex(), scoreOf(m)));
        }
        return new ArrayList<>(seen.values());
    }

    private static Integer pageOf(Map<String, Object> metadata) {
        Object page = metadata.getOrDefault("page_number", metadata.get("page"));
        if (page instanceof Number n) {
            return n.intValue();
        }
        try {
            return page == null ? null : Integer.valueOf(page.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double scoreOf(Map<String, Object> metadata) {
        Object score = metadata.get(RetrievalPostProcessor.SCORE_KEY);
        return score instanceof Number n ? n.doubleValue() : null;
    }

    private static int parseInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String str(Object value) {
        return Objects.toString(value, "");
    }
}
