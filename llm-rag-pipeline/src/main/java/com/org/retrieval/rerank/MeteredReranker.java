package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Decorator that wraps a {@link Reranker} with Micrometer metrics:
 * {@code rag.rerank.duration} timer and {@code rag.rerank.failures} counter.
 * The inner reranker stays focused on ranking logic only.
 */
@Slf4j
public class MeteredReranker implements Reranker {

    private final Reranker delegate;
    private final MeterRegistry meterRegistry;

    /**
     * Wraps {@code delegate}, recording duration/failure metrics via {@code meterRegistry}.
     */
    public MeteredReranker(Reranker delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RerankStrategy strategy() {
        return delegate.strategy();
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            List<Chunk> result = delegate.rerank(query, chunks);
            sample.stop(meterRegistry.timer("rag.rerank.duration", "strategy", strategy().name()));
            return result;
        } catch (Exception e) {
            meterRegistry.counter("rag.rerank.failures", "strategy", strategy().name()).increment();
            throw e;
        }
    }
}
