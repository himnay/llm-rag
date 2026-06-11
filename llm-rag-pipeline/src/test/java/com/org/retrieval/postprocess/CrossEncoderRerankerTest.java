package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalProperties;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reranker is opt-in and must never break retrieval when it can't run. These guard tests cover
 * the no-op paths (disabled, or enabled without an API key) without making any network call.
 */
class CrossEncoderRerankerTest {

    private final List<Chunk> chunks = List.of(
            new Chunk("WIKI", "alpha", new HashMap<>(), 0),
            new Chunk("WIKI", "beta", new HashMap<>(), 1));

    @Test
    void disabledPassesThroughUnchanged() {
        RetrievalProperties props = new RetrievalProperties(); // rerank disabled by default
        assertThat(new CrossEncoderReranker(props).process("q", chunks)).isEqualTo(chunks);
    }

    @Test
    void enabledButBlankApiKeyPassesThrough() {
        RetrievalProperties props = new RetrievalProperties();
        props.getRerank().setEnabled(true); // apiKey left blank
        assertThat(new CrossEncoderReranker(props).process("q", chunks)).isEqualTo(chunks);
    }
}
