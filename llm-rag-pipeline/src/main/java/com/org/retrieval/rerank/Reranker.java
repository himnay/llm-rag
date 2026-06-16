package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.retrieval.postprocess.RetrievalPostProcessor;

import java.util.List;

/**
 * One reranking technique. Implementations re-order the candidates by their true relevance to the
 * query and write the new relevance into each chunk's {@link RetrievalPostProcessor#SCORE_KEY}
 * metadata (so the downstream {@code ScoreAwareRanker} preserves the order).
 *
 * <p>Implementations may throw on infrastructure failure — {@link RerankingPostProcessor} catches
 * and degrades gracefully to the incoming order.</p>
 */
public interface Reranker {

    /**
     * Stamp a chunk's relevance score so downstream ranking orders by it.
     */
    static void score(Chunk chunk, double score) {
        chunk.metadata().put(RetrievalPostProcessor.SCORE_KEY, score);
    }

    /**
     * Which {@code app.retrieval.rerank.strategy} value selects this implementation.
     */
    RerankStrategy strategy();

    List<Chunk> rerank(String query, List<Chunk> chunks);
}
