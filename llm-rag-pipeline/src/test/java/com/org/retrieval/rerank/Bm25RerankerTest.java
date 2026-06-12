package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Bm25RerankerTest {

    private final Bm25Reranker reranker = new Bm25Reranker();

    @Test
    void ranksKeywordMatchFirstAndNormalizesScores() {
        List<Chunk> chunks = List.of(
                new Chunk("WIKI", "the office is closed on public holidays", new HashMap<>(), 0),
                new Chunk("WIKI", "error code E1234 means the printer is out of toner", new HashMap<>(), 1),
                new Chunk("WIKI", "lunch vouchers are available at reception", new HashMap<>(), 2));

        List<Chunk> result = reranker.rerank("what does error E1234 mean", chunks);

        assertThat(result.get(0).content()).contains("E1234");
        assertThat(RetrievalPostProcessor.score(result.get(0))).isEqualTo(1.0); // max-normalized
        assertThat(result).containsExactlyInAnyOrderElementsOf(chunks);
    }

    @Test
    void noLexicalOverlapScoresZeroWithoutThrowing() {
        List<Chunk> chunks = List.of(
                new Chunk("WIKI", "alpha", new HashMap<>(), 0),
                new Chunk("WIKI", "beta", new HashMap<>(), 1));
        List<Chunk> result = reranker.rerank("zzz", chunks);
        assertThat(result).hasSize(2);
        assertThat(RetrievalPostProcessor.score(result.get(0))).isZero();
    }
}
