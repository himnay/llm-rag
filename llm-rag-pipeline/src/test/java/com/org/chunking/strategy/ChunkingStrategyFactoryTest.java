package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkingStrategyFactoryTest {

    private final ChunkingStrategyFactory factory = buildFactory();

    private static ChunkingStrategyFactory buildFactory() {
        ChunkingStrategyFactory f = new ChunkingStrategyFactory(List.of(named("recursive"), named("token")));
        f.init();
        return f;
    }

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
    @DisplayName("Resolves a registered strategy by name regardless of case")
    void resolvesByNameCaseInsensitively() {
        assertThat(factory.get("RECURSIVE").name()).isEqualTo("recursive");
        assertThat(factory.has("token")).isTrue();
        assertThat(factory.has("nope")).isFalse();
    }

    @Test
    @DisplayName("Requesting an unknown strategy name throws UnknownChunkingStrategyException")
    void unknownStrategyThrows() {
        assertThatThrownBy(() -> factory.get("nope"))
                .isInstanceOf(UnknownChunkingStrategyException.class)
                .hasMessageContaining("Unknown chunking strategy");
    }
}
