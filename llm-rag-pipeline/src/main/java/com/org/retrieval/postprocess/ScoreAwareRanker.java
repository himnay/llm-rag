package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import com.org.retrieval.ChunkRankingComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Primary ordering by relevance score (vector similarity, or rerank score when a reranker ran),
 * with the domain priority rules ({@link ChunkRankingComparator}: source/table/recency) used only
 * as a tie-breaker. This fixes the previous behaviour where business priority overrode relevance.
 */
@Component
public class ScoreAwareRanker implements RetrievalPostProcessor {

    private final Comparator<Chunk> comparator =
            Comparator.comparingDouble(RetrievalPostProcessor::score).reversed()
                    .thenComparing(new ChunkRankingComparator());

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public List<Chunk> process(String query, List<Chunk> chunks) {
        List<Chunk> ranked = new ArrayList<>(chunks);
        ranked.sort(comparator);
        return ranked;
    }
}
