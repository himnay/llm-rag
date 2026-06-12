package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import com.org.retrieval.postprocess.RetrievalPostProcessor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionRerankerTest {

    private final RrfFusionReranker reranker = new RrfFusionReranker();

    @Test
    void fusesDenseOrderWithLexicalEvidence() {
        // Dense ranking (incoming order) puts the lexical match last. RRF credits both rankings:
        // winning BM25 lifts it above the lexically-irrelevant chunk ranked just above it, while
        // the dense leader (top of one ranking, mid-table in the other) stays first.
        List<Chunk> chunks = List.of(
                new Chunk("WIKI", "company culture and values", new HashMap<>(), 0),
                new Chunk("WIKI", "team events calendar", new HashMap<>(), 1),
                new Chunk("WIKI", "the vpn client setup guide for remote access", new HashMap<>(), 2));

        List<Chunk> result = reranker.rerank("vpn setup", chunks);

        assertThat(result).extracting(Chunk::content).containsExactly(
                "company culture and values",
                "the vpn client setup guide for remote access",
                "team events calendar");
        assertThat(RetrievalPostProcessor.score(result.get(0))).isEqualTo(1.0);
    }

    @Test
    void blankQueryKeepsDenseOrder() {
        List<Chunk> chunks = List.of(
                new Chunk("WIKI", "first", new HashMap<>(), 0),
                new Chunk("WIKI", "second", new HashMap<>(), 1));
        List<Chunk> result = reranker.rerank("", chunks);
        assertThat(result.get(0).content()).isEqualTo("first");
    }
}
