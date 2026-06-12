package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lexical reranking with Okapi BM25 computed over the candidate set. Complements dense retrieval
 * where embeddings are weak — exact keywords, product codes, error strings, names — at zero cost
 * and zero latency (no model or network call). Scores are max-normalized to 0..1 so they remain
 * comparable with the similarity scores carried in chunk metadata.
 */
@Component
public class Bm25Reranker implements Reranker {

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.BM25;
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        double[] scores = Bm25.scores(query, chunks);
        double max = 0;
        for (double s : scores) {
            max = Math.max(max, s);
        }
        for (int i = 0; i < chunks.size(); i++) {
            Reranker.score(chunks.get(i), max == 0 ? 0.0 : scores[i] / max);
        }
        List<Chunk> reranked = new ArrayList<>(chunks);
        reranked.sort(Comparator.comparingDouble(
                (Chunk c) -> com.org.retrieval.postprocess.RetrievalPostProcessor.score(c)).reversed());
        return reranked;
    }
}
