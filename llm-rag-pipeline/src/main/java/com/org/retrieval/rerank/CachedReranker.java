package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decorator that adds per-chunk score caching to a {@link Reranker}.
 * On a full cache hit for all candidates the inner reranker is bypassed entirely.
 * Only wraps costly strategies (cross-encoder, LLM-based) where the saving matters.
 */
@Slf4j
public class CachedReranker implements Reranker {

    private final Reranker delegate;
    private final RerankScoreCache cache;
    private final MeterRegistry meterRegistry;

    /**
     * Wraps {@code delegate}, scoring from {@code cache} on a full hit and recording metrics via
     * {@code meterRegistry} otherwise.
     */
    public CachedReranker(Reranker delegate, RerankScoreCache cache, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.cache = cache;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RerankStrategy strategy() {
        return delegate.strategy();
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        if (!strategy().isCostly()) {
            return delegate.rerank(query, chunks);
        }
        List<Chunk> rescored = tryFromCache(query, chunks);
        if (rescored != null) {
            meterRegistry.counter("rag.rerank.cache.hits", "strategy", strategy().name()).increment();
            log.debug("Rerank cache hit for strategy={} chunks={}", strategy(), chunks.size());
            return rescored;
        }
        List<Chunk> result = delegate.rerank(query, chunks);
        storeInCache(query, result);
        return result;
    }

    private List<Chunk> tryFromCache(String query, List<Chunk> chunks) {
        List<Chunk> rescored = new ArrayList<>(chunks);
        for (Chunk chunk : rescored) {
            Double score = cache.get(RerankScoreCache.key(strategy(), query, chunk.content()));
            if (score == null) return null;
            Reranker.score(chunk, score);
        }
        rescored.sort(Comparator.comparingDouble(RetrievalPostProcessor::score).reversed());
        return rescored;
    }

    private void storeInCache(String query, List<Chunk> reranked) {
        for (Chunk chunk : reranked) {
            cache.put(RerankScoreCache.key(strategy(), query, chunk.content()),
                    RetrievalPostProcessor.score(chunk));
        }
    }
}
