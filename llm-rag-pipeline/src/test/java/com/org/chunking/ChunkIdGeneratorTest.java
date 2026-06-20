package com.org.chunking;

import com.org.chunking.model.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkIdGeneratorTest {

    @Test
    @DisplayName("Same identity, source and chunkIndex always produce the same id")
    void deterministicForSameInputs() {
        String id1 = ChunkIdGenerator.idFor("PDF#leave.pdf", "PDF", 2);
        String id2 = ChunkIdGenerator.idFor("PDF#leave.pdf", "PDF", 2);
        assertThat(id1).isEqualTo(id2);
    }

    @Test
    @DisplayName("Different chunkIndex produces a different id")
    void differentChunkIndexProducesDifferentId() {
        String id1 = ChunkIdGenerator.idFor("PDF#leave.pdf", "PDF", 0);
        String id2 = ChunkIdGenerator.idFor("PDF#leave.pdf", "PDF", 1);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Different identity produces a different id")
    void differentIdentityProducesDifferentId() {
        String id1 = ChunkIdGenerator.idFor("PDF#a.pdf", "PDF", 0);
        String id2 = ChunkIdGenerator.idFor("PDF#b.pdf", "PDF", 0);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Different source produces a different id")
    void differentSourceProducesDifferentId() {
        String id1 = ChunkIdGenerator.idFor("X", "PDF", 0);
        String id2 = ChunkIdGenerator.idFor("X", "WIKI", 0);
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("idFor(Chunk) reads identity from chunk metadata and matches the explicit overload")
    void idForChunkMatchesExplicitOverload() {
        Chunk chunk = new Chunk("PDF", "content", Map.of("identity", "PDF#leave.pdf"), 3);
        assertThat(ChunkIdGenerator.idFor(chunk)).isEqualTo(ChunkIdGenerator.idFor("PDF#leave.pdf", "PDF", 3));
    }

    @Test
    @DisplayName("idFor(Chunk) treats a missing identity as an empty string, not a failure")
    void idForChunkHandlesMissingIdentity() {
        Chunk chunk = new Chunk("PDF", "content", Map.of(), 0);
        assertThat(ChunkIdGenerator.idFor(chunk)).isEqualTo(ChunkIdGenerator.idFor("", "PDF", 0));
    }

    @Test
    @DisplayName("Produces a 64-char hex SHA-256 digest")
    void producesShaHexDigest() {
        String id = ChunkIdGenerator.idFor("id", "PDF", 0);
        assertThat(id).hasSize(64).matches("[0-9a-f]+");
    }
}
