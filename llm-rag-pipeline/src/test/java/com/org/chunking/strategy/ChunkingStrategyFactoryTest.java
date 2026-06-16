package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingStrategyFactoryTest {

    private final ChunkingStrategyFactory factory =
            new ChunkingStrategyFactory(List.of(named("recursive"), named("token")));

    private static ChunkingStrategy named(String name) {
        return new ChunkingStrategy() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<Chunk> chunk(IngestedDocument d) {
                return List.of();
            }
        };
    }

    @Test
    void resolvesByNameCaseInsensitively() {
        assertThat(factory.get("RECURSIVE").name()).isEqualTo("recursive");
        assertThat(factory.has("token")).isTrue();
        assertThat(factory.has("nope")).isFalse();
    }

    @Test
    void unknownStrategyThrows() {
        assertThatThrownBy(() -> factory.get("nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown chunking strategy");
    }
}
