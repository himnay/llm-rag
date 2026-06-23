package com.org.retrieval;

import com.org.chunking.model.Chunk;
import com.org.exception.SearchStrategyNotFoundException;
import com.org.retrieval.model.Citation;
import com.org.retrieval.model.RetrievalResult;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import com.org.retrieval.search.SearchMode;
import com.org.retrieval.search.SearchStrategy;
import com.org.retrieval.transform.QueryTransformMode;
import com.org.retrieval.transform.QueryTransformationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrieval API — the "R" of this RAG service. Optionally transforms the query (rewrite, HyDE,
 * multi-query, step-back) via {@link QueryTransformationService}, over-fetches candidates via the
 * configured {@link SearchStrategy} (vector kNN, keyword BM25, or hybrid RRF), runs them through
 * the ordered {@link RetrievalPostProcessor} chain (business rules → length → dedup → rerank →
 * score-aware ranking → MMR), trims to {@code topK}, and attaches {@link Citation}s for provenance.
 *
 * <p>For multi-query mode, each variant query produces its own candidate set; the sets are merged
 * and deduplicated by document ID before post-processing.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final RetrievalProperties properties;
    private final List<SearchStrategy> searchStrategyList;
    private final List<RetrievalPostProcessor> rawPostProcessors;
    private final QueryTransformationService queryTransformationService;
    private final ChunkHydrationService chunkHydrationService;
    private final RetrievalStrategyClassifier retrievalStrategyClassifier;

    private final Map<SearchMode, SearchStrategy> searchStrategies = new EnumMap<>(SearchMode.class);
    private List<RetrievalPostProcessor> postProcessors;

    /**
     * Retrieve using the configured default {@code topK}.
     */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, properties.getDefaultTopK());
    }

    /**
     * Retrieve the top-{@code topK} chunks for a query, post-processed and ranked, with citations.
     */
    public RetrievalResult retrieve(String query, int topK) {
        int k = topK > 0 ? topK : properties.getDefaultTopK();
        int fetch = k * Math.max(1, properties.getOverFetchFactor());
        RetrievalStrategy retrievalStrategy = properties.getClassifier().isEnabled()
                ? retrievalStrategyClassifier.classify(query)
                : new RetrievalStrategy(properties.getSearch().getMode(), properties.getQueryTransform().getMode());
        SearchMode mode = retrievalStrategy.searchMode();
        QueryTransformMode transformMode = retrievalStrategy.transformMode();

        log.info("Retrieval requested | query='{}' | topK={} | fetch={} | search={} | transform={}",
                query, k, fetch, mode, transformMode);

        List<String> queries = queryTransformationService.transform(query, transformMode);
        log.info("Query transform produced {} query/queries", queries.size());

        SearchStrategy strategy = searchStrategies.get(mode);
        if (strategy == null) {
            throw new SearchStrategyNotFoundException("No search strategy registered for mode " + mode);
        }

        List<Document> documents = searchAndMerge(strategy, queries, fetch);
        log.info("{} search returned {} candidate document(s) (after merge)", mode, documents.size());

        List<Chunk> chunks = documents.stream().map(this::toChunk).collect(Collectors.toList());
        chunks = chunkHydrationService.hydrate(chunks);
        if (log.isDebugEnabled()) {
            log.debug("Retrieved chunks before post-processing (count={}):", chunks.size());
            chunks.forEach(c -> log.debug("  chunk source='{}' index={} score={} textPreview='{}'",
                    c.source(), c.chunkIndex(),
                    c.metadata().get(RetrievalPostProcessor.SCORE_KEY),
                    c.content().length() > 120 ? c.content().substring(0, 120) + "…" : c.content()));
        }
        for (RetrievalPostProcessor processor : postProcessors) {
            int before = chunks.size();
            chunks = processor.process(query, chunks);
            log.debug("Post-processor '{}' (order {}) → {}/{} chunk(s) retained",
                    processor.getClass().getSimpleName(), processor.getOrder(), chunks.size(), before);
        }
        if (chunks.size() > k) {
            chunks = new ArrayList<>(chunks.subList(0, k));
        }
        return new RetrievalResult().chunks(chunks).citations(toCitations(chunks));
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

    @PostConstruct
    void init() {
        searchStrategyList.forEach(s -> searchStrategies.put(s.mode(), s));
        postProcessors = rawPostProcessors.stream()
                .sorted(Comparator.comparingInt(RetrievalPostProcessor::getOrder))
                .toList();
    }

    /**
     * Search for each query variant and merge results, deduplicating by document ID.
     */
    private List<Document> searchAndMerge(SearchStrategy strategy, List<String> queries, int fetch) {
        if (queries.size() == 1) {
            return strategy.search(queries.get(0), fetch);
        }
        LinkedHashMap<String, Document> seen = new LinkedHashMap<>();
        for (String q : queries) {
            for (Document doc : strategy.search(q, fetch)) {
                String key = doc.getId() != null && !doc.getId().isBlank()
                        ? doc.getId() : doc.getText();
                seen.putIfAbsent(key, doc);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private Chunk toChunk(Document document) {
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        if (document.getScore() != null) {
            metadata.put(RetrievalPostProcessor.SCORE_KEY, document.getScore());
        }
        return new Chunk(str(metadata.get("source")), document.getText(),
                metadata, parseInt(metadata.get("chunkIndex")));
    }

    /**
     * One citation per distinct (identity, page, chunk), preserving rank order.
     */
    private List<Citation> toCitations(List<Chunk> chunks) {
        LinkedHashMap<String, Citation> seen = new LinkedHashMap<>();
        for (Chunk chunk : chunks) {
            Map<String, Object> m = chunk.metadata();
            String identity = str(m.get("identity"));
            Integer page = pageOf(m);
            String key = identity + "#" + page + "#" + chunk.chunkIndex();
            seen.putIfAbsent(key, new Citation()
                    .source(chunk.source())
                    .fileName(str(m.get("fileName")))
                    .identity(identity.isEmpty() ? null : identity)
                    .page(page)
                    .chunkIndex(chunk.chunkIndex())
                    .score(scoreOf(m)));
        }
        return new ArrayList<>(seen.values());
    }
}
