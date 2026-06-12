package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.common.CircuitBreaker;
import com.org.retrieval.RetrievalProperties;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The rerank stage of the retrieval chain (order 40, between dedup and score-aware ranking).
 * Delegates to the {@link Reranker} selected by {@code app.retrieval.rerank.strategy} and applies
 * the cross-cutting safety rails so individual rerankers stay simple:
 *
 * <ul>
 *   <li><b>Opt-in</b> — disabled by default ({@code app.retrieval.rerank.enabled}).</li>
 *   <li><b>Cost cap</b> — only the top {@code top-n} candidates are re-scored (they're already
 *       vector-ranked); the tail keeps its original order below them.</li>
 *   <li><b>Score cache</b> — for the costly strategies, per-chunk scores are cached
 *       ({@link RerankScoreCache}); a repeated query over unchanged candidates re-ranks for free.</li>
 *   <li><b>Circuit breaker</b> — consecutive failures open a {@link CircuitBreaker} so a down
 *       vendor/model is skipped instead of paying its timeout on every request.</li>
 *   <li><b>Relevance floor</b> — {@code min-score} drops re-scored chunks the reranker judged
 *       irrelevant (improves context precision for the downstream LLM).</li>
 *   <li><b>Fail-open</b> — any reranker failure logs a warning and passes the candidates through
 *       unchanged; reranking must never break retrieval.</li>
 *   <li><b>Metrics</b> — Micrometer timer {@code rag.rerank.duration} and counters
 *       {@code rag.rerank.failures} / {@code rag.rerank.cache.hits} /
 *       {@code rag.rerank.breaker.rejected}, all tagged by strategy.</li>
 * </ul>
 */
@Slf4j
@Component
public class RerankingPostProcessor implements RetrievalPostProcessor {

    private final RetrievalProperties properties;
    private final Map<RerankStrategy, Reranker> rerankers = new EnumMap<>(RerankStrategy.class);
    private final MeterRegistry meterRegistry;
    private final CircuitBreaker breaker;
    private final RerankScoreCache cache;

    public RerankingPostProcessor(RetrievalProperties properties, List<Reranker> rerankers,
                                  MeterRegistry meterRegistry) {
        this.properties = properties;
        rerankers.forEach(r -> this.rerankers.put(r.strategy(), r));
        this.meterRegistry = meterRegistry;
        RetrievalProperties.Rerank cfg = properties.getRerank();
        this.breaker = new CircuitBreaker(cfg.getBreaker().getFailureThreshold(), cfg.getBreaker().getCooldown());
        this.cache = cfg.getCache().isEnabled()
                ? new RerankScoreCache(cfg.getCache().getMaxSize(), cfg.getCache().getTtl())
                : null;
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public List<Chunk> process(String query, List<Chunk> chunks) {
        RetrievalProperties.Rerank cfg = properties.getRerank();
        if (!cfg.isEnabled() || chunks.size() < 2) {
            return chunks;
        }
        Reranker reranker = rerankers.get(cfg.getStrategy());
        if (reranker == null) {
            log.warn("No reranker registered for strategy {} — passing candidates through", cfg.getStrategy());
            return chunks;
        }
        int topN = cfg.getTopN() > 0 ? Math.min(cfg.getTopN(), chunks.size()) : chunks.size();
        List<Chunk> head = chunks.subList(0, topN);
        List<Chunk> tail = chunks.subList(topN, chunks.size());

        List<Chunk> fromCache = fromCache(cfg, query, head);
        if (fromCache != null) {
            meterRegistry.counter("rag.rerank.cache.hits", "strategy", cfg.getStrategy().name()).increment();
            return combine(cfg, fromCache, tail);
        }
        if (!breaker.allowRequest()) {
            meterRegistry.counter("rag.rerank.breaker.rejected", "strategy", cfg.getStrategy().name()).increment();
            log.warn("{} rerank circuit open — passing candidates through unchanged", cfg.getStrategy());
            return chunks;
        }
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            List<Chunk> reranked = reranker.rerank(query, head);
            sample.stop(meterRegistry.timer("rag.rerank.duration", "strategy", cfg.getStrategy().name()));
            breaker.recordSuccess();
            storeInCache(cfg, query, reranked);
            log.info("Reranked {}/{} candidate(s) with {}", topN, chunks.size(), cfg.getStrategy());
            return combine(cfg, reranked, tail);
        } catch (Exception e) {
            breaker.recordFailure();
            meterRegistry.counter("rag.rerank.failures", "strategy", cfg.getStrategy().name()).increment();
            log.warn("{} reranking failed ({}); passing candidates through unchanged",
                    cfg.getStrategy(), e.getMessage());
            return chunks;
        }
    }

    /** Re-scored head + untouched tail, with the relevance floor applied to the re-scored part. */
    private static List<Chunk> combine(RetrievalProperties.Rerank cfg, List<Chunk> reranked, List<Chunk> tail) {
        List<Chunk> result = new ArrayList<>(reranked.size() + tail.size());
        for (Chunk chunk : reranked) {
            if (cfg.getMinScore() <= 0 || RetrievalPostProcessor.score(chunk) >= cfg.getMinScore()) {
                result.add(chunk);
            }
        }
        result.addAll(tail);
        return result;
    }

    /** The head re-ranked from cached scores, or {@code null} unless every candidate is a hit. */
    private List<Chunk> fromCache(RetrievalProperties.Rerank cfg, String query, List<Chunk> head) {
        if (cache == null || !cfg.getStrategy().isCostly()) {
            return null;
        }
        List<Chunk> rescored = new ArrayList<>(head);
        for (Chunk chunk : rescored) {
            Double score = cache.get(RerankScoreCache.key(cfg.getStrategy(), query, chunk.content()));
            if (score == null) {
                return null;
            }
            Reranker.score(chunk, score);
        }
        rescored.sort(Comparator.comparingDouble(RetrievalPostProcessor::score).reversed());
        return rescored;
    }

    private void storeInCache(RetrievalProperties.Rerank cfg, String query, List<Chunk> reranked) {
        if (cache == null || !cfg.getStrategy().isCostly()) {
            return;
        }
        for (Chunk chunk : reranked) {
            cache.put(RerankScoreCache.key(cfg.getStrategy(), query, chunk.content()),
                    RetrievalPostProcessor.score(chunk));
        }
    }
}
