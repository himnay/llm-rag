package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.common.CircuitBreaker;
import com.org.retrieval.RetrievalProperties;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The rerank stage of the retrieval chain (order 40, between dedup and score-aware ranking).
 * Delegates to the {@link Reranker} selected by {@code app.retrieval.rerank.strategy}.
 *
 * <p>Cross-cutting concerns (caching, metrics, circuit breaker) are applied via the
 * {@link CachedReranker} and {@link MeteredReranker} decorators at construction time so
 * individual reranker implementations stay focused on their scoring logic.</p>
 *
 * <ul>
 *   <li><b>Opt-in</b> — disabled by default ({@code app.retrieval.rerank.enabled}).</li>
 *   <li><b>Cost cap</b> — only the top {@code top-n} candidates are re-scored.</li>
 *   <li><b>Circuit breaker</b> — consecutive failures open the breaker so a down vendor is
 *       skipped rather than timing out on every request.</li>
 *   <li><b>Relevance floor</b> — {@code min-score} drops re-scored chunks the reranker judged
 *       irrelevant.</li>
 *   <li><b>Fail-open</b> — any reranker failure passes the candidates through unchanged;
 *       reranking must never break retrieval.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RerankingPostProcessor implements RetrievalPostProcessor {

    private final RetrievalProperties properties;
    private final List<Reranker> rawRerankers;
    private final MeterRegistry meterRegistry;

    /**
     * Decorated rerankers: original → MeteredReranker → CachedReranker (for costly strategies).
     */
    private final Map<RerankStrategy, Reranker> rerankers = new EnumMap<>(RerankStrategy.class);
    private CircuitBreaker breaker;

    @PostConstruct
    void init() {
        RetrievalProperties.Rerank cfg = properties.getRerank();
        breaker = new CircuitBreaker(cfg.getBreaker().getFailureThreshold(), cfg.getBreaker().getCooldown());
        RerankScoreCache cache = cfg.getCache().isEnabled()
                ? new RerankScoreCache(cfg.getCache().getMaxSize(), cfg.getCache().getTtl()) : null;
        for (Reranker r : rawRerankers) {
            Reranker decorated = new MeteredReranker(r, meterRegistry);
            if (cache != null && r.strategy().isCostly()) {
                decorated = new CachedReranker(decorated, cache, meterRegistry);
            }
            rerankers.put(r.strategy(), decorated);
        }
    }

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

        if (!breaker.allowRequest()) {
            meterRegistry.counter("rag.rerank.breaker.rejected", "strategy", cfg.getStrategy().name()).increment();
            log.warn("{} rerank circuit open — passing candidates through unchanged", cfg.getStrategy());
            return chunks;
        }
        try {
            log.debug("Reranking {} candidate(s) with strategy={} query='{}'", topN, cfg.getStrategy(), query);
            List<Chunk> reranked = reranker.rerank(query, head);
            breaker.recordSuccess();
            log.info("Reranked {}/{} candidate(s) with {}", topN, chunks.size(), cfg.getStrategy());
            if (log.isDebugEnabled()) {
                reranked.forEach(c -> log.debug("  reranked chunk source='{}' index={} score={}",
                        c.source(), c.chunkIndex(), RetrievalPostProcessor.score(c)));
            }
            return combine(cfg, reranked, tail);
        } catch (Exception e) {
            breaker.recordFailure();
            log.warn("{} reranking failed ({}); passing candidates through unchanged",
                    cfg.getStrategy(), e.getMessage());
            return chunks;
        }
    }
}
