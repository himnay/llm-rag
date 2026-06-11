package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveChunkingStrategyTest {

    private RecursiveChunkingStrategy strategy(int maxChars, int overlap) {
        ChunkingProperties props = new ChunkingProperties();
        props.setMaxChars(maxChars);
        props.setOverlap(overlap);
        return new RecursiveChunkingStrategy(props);
    }

    @Test
    void shortTextProducesSingleChunk() {
        IngestedDocument doc = new IngestedDocument("FILE", "short text", Map.of("identity", "FILE#x"));
        List<Chunk> chunks = strategy(1000, 100).chunk(doc);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("short text");
        assertThat(chunks.get(0).metadata()).containsEntry("chunkStrategy", "recursive");
    }

    @Test
    void longTextSplitsIntoMultipleBoundedChunks() {
        String para = "Sentence one is here. Sentence two follows it. ".repeat(20);
        String text = para + "\n\n" + para;
        IngestedDocument doc = new IngestedDocument("FILE", text, Map.of("identity", "FILE#x"));

        List<Chunk> chunks = strategy(200, 40).chunk(doc);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> {
            assertThat(c.content()).isNotBlank();
            assertThat(c.content().length()).isLessThanOrEqualTo(260); // maxChars + small separator slack
        });
        // chunk indexes are sequential from 0
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
    }
}
