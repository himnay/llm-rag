package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hybrid reranking via Reciprocal Rank Fusion: fuses the incoming dense (vector-similarity) ranking
 * with a lexical BM25 ranking using {@code score = Σ 1 / (k + rank)}. Rank-based fusion sidesteps
 * the classic problem that dense and lexical scores live on incomparable scales. Free and fast —
 * no model or network call.
 */
@Component
public class RrfFusionReranker implements Reranker {

    /** Standard RRF damping constant from the original Cormack et al. paper. */
    private static final int K = 60;

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.RRF;
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        // Ranking 1: the incoming order (vector similarity, best first).
        // Ranking 2: BM25 over the same candidates.
        double[] bm25 = Bm25.scores(query, chunks);
        Integer[] byBm25 = new Integer[chunks.size()];
        for (int i = 0; i < byBm25.length; i++) {
            byBm25[i] = i;
        }
        java.util.Arrays.sort(byBm25, Comparator.comparingDouble((Integer i) -> bm25[i]).reversed());

        double[] fused = new double[chunks.size()];
        for (int rank = 0; rank < chunks.size(); rank++) {
            fused[rank] += 1.0 / (K + rank + 1);            // dense ranking contribution
            fused[byBm25[rank]] += 1.0 / (K + rank + 1);    // lexical ranking contribution
        }

        double max = 0;
        for (double s : fused) {
            max = Math.max(max, s);
        }
        for (int i = 0; i < chunks.size(); i++) {
            Reranker.score(chunks.get(i), max == 0 ? 0.0 : fused[i] / max);
        }
        List<Chunk> reranked = new ArrayList<>(chunks);
        reranked.sort(Comparator.comparingDouble(
                (Chunk c) -> com.org.retrieval.postprocess.RetrievalPostProcessor.score(c)).reversed());
        return reranked;
    }
}
