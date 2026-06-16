package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reranking is opt-in and must never break retrieval: these tests cover the no-op paths (disabled,
 * unknown strategy), the safety rails (fail-open, circuit breaker, top-n cost cap, score cache,
 * min-score floor) — all without any network call.
 */
class RerankingPostProcessorTest {

    private final List<Chunk> chunks = List.of(
            new Chunk("WIKI", "alpha", new HashMap<>(), 0),
            new Chunk("WIKI", "beta", new HashMap<>(), 1),
            new Chunk("WIKI", "gamma", new HashMap<>(), 2));

    private static RetrievalProperties enabled(RerankStrategy strategy) {
        RetrievalProperties props = new RetrievalProperties();
        props.getRerank().setEnabled(true);
        props.getRerank().setStrategy(strategy);
        return props;
    }

    private static RerankingPostProcessor processor(RetrievalProperties props, Reranker... rerankers) {
        RerankingPostProcessor p = new RerankingPostProcessor(props, List.of(rerankers), new SimpleMeterRegistry());
        p.init();
        return p;
    }

    @Test
    void disabledPassesThroughUnchanged() {
        RetrievalProperties props = new RetrievalProperties(); // rerank disabled by default
        assertThat(processor(props, new Bm25Reranker()).process("q", chunks)).isEqualTo(chunks);
    }

    @Test
    void unknownStrategyPassesThrough() {
        RerankingPostProcessor p = processor(enabled(RerankStrategy.CROSS_ENCODER), new Bm25Reranker());
        assertThat(p.process("q", chunks)).isEqualTo(chunks);
    }

    @Test
    void rerankerFailurePassesThrough() {
        CountingReranker failing = new CountingReranker(RerankStrategy.BM25, true);
        RerankingPostProcessor p = processor(enabled(RerankStrategy.BM25), failing);
        assertThat(p.process("q", chunks)).isEqualTo(chunks);
    }

    @Test
    void delegatesToSelectedStrategy() {
        RerankingPostProcessor p = processor(enabled(RerankStrategy.BM25), new Bm25Reranker());
        List<Chunk> result = p.process("gamma", chunks);
        assertThat(result.get(0).content()).isEqualTo("gamma");
        assertThat(result).containsExactlyInAnyOrderElementsOf(chunks);
    }

    @Test
    void topNRescoresOnlyTheHeadAndKeepsTheTail() {
        RetrievalProperties props = enabled(RerankStrategy.BM25);
        props.getRerank().setTopN(2);
        // "gamma" matches the 3rd chunk, but it sits outside top-n=2, so it must keep its slot.
        List<Chunk> result = processor(props, new Bm25Reranker()).process("gamma", chunks);
        assertThat(result.get(2).content()).isEqualTo("gamma");
    }

    @Test
    void breakerOpensAfterConsecutiveFailuresAndSkipsTheVendor() {
        RetrievalProperties props = enabled(RerankStrategy.BM25);
        props.getRerank().getBreaker().setFailureThreshold(2);
        props.getRerank().getBreaker().setCooldown(Duration.ofMinutes(10));
        CountingReranker failing = new CountingReranker(RerankStrategy.BM25, true);
        RerankingPostProcessor p = processor(props, failing);

        for (int i = 0; i < 5; i++) {
            assertThat(p.process("q", chunks)).isEqualTo(chunks); // always fail-open
        }
        assertThat(failing.invocations).hasValue(2); // calls 3..5 rejected by the open breaker
    }

    @Test
    void costlyStrategyScoresAreCachedAcrossCalls() {
        CountingReranker counting = new CountingReranker(RerankStrategy.CROSS_ENCODER, false);
        RerankingPostProcessor p = processor(enabled(RerankStrategy.CROSS_ENCODER), counting);

        List<Chunk> first = p.process("q", chunks);
        List<Chunk> second = p.process("q", chunks);

        assertThat(counting.invocations).hasValue(1); // second call served from the score cache
        assertThat(second).extracting(Chunk::content)
                .containsExactlyElementsOf(first.stream().map(Chunk::content).toList());
    }

    @Test
    void cheapStrategiesBypassTheCache() {
        CountingReranker counting = new CountingReranker(RerankStrategy.BM25, false);
        RerankingPostProcessor p = processor(enabled(RerankStrategy.BM25), counting);
        p.process("q", chunks);
        p.process("q", chunks);
        assertThat(counting.invocations).hasValue(2);
    }

    @Test
    void minScoreDropsChunksTheRerankerJudgedIrrelevant() {
        RetrievalProperties props = enabled(RerankStrategy.BM25);
        props.getRerank().setMinScore(0.5);
        // BM25: only "gamma" matches the query (score 1.0); the others score 0 and are dropped.
        List<Chunk> result = processor(props, new Bm25Reranker()).process("gamma", chunks);
        assertThat(result).extracting(Chunk::content).containsExactly("gamma");
    }

    /**
     * Counting reranker that stamps descending scores by reversed input order.
     */
    private static final class CountingReranker implements Reranker {
        final AtomicInteger invocations = new AtomicInteger();
        final RerankStrategy strategy;
        final boolean failing;

        CountingReranker(RerankStrategy strategy, boolean failing) {
            this.strategy = strategy;
            this.failing = failing;
        }

        @Override
        public RerankStrategy strategy() {
            return strategy;
        }

        @Override
        public List<Chunk> rerank(String query, List<Chunk> candidates) {
            invocations.incrementAndGet();
            if (failing) {
                throw new IllegalStateException("boom");
            }
            List<Chunk> reversed = new java.util.ArrayList<>(candidates).reversed();
            for (int i = 0; i < reversed.size(); i++) {
                Reranker.score(reversed.get(i), 1.0 - i * 0.1);
            }
            return reversed;
        }
    }
}
