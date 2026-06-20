package com.org.retrieval;

import com.org.chunking.model.Chunk;
import com.org.mongo.ChunkDocument;
import com.org.mongo.ChunkDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkHydrationServiceTest {

    private final ChunkDocumentRepository repository = mock(ChunkDocumentRepository.class);
    private final ChunkHydrationService service = new ChunkHydrationService(repository);

    @Test
    @DisplayName("Replaces chunk content with Mongo's copy and merges metadata, retrieval-time metadata winning")
    void hydratesContentAndMergesMetadata() {
        Chunk chunk = new Chunk("PDF", "stale search-index text",
                Map.of("chunkId", "abc", "score", 0.9), 0);
        ChunkDocument doc = new ChunkDocument();
        doc.setContent("fresh mongo text");
        doc.setMetadata(Map.of("fileName", "leave.pdf", "score", 0.1)); // stale score from Mongo
        when(repository.findByIds(anyCollection())).thenReturn(Map.of("abc", doc));

        List<Chunk> result = service.hydrate(List.of(chunk));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("fresh mongo text");
        assertThat(result.get(0).metadata()).containsEntry("fileName", "leave.pdf");
        assertThat(result.get(0).metadata()).containsEntry("score", 0.9); // retrieval-time wins
    }

    @Test
    @DisplayName("Passes a chunk through unchanged when it has no chunkId")
    void passthroughWhenNoChunkId() {
        Chunk chunk = new Chunk("PDF", "content", Map.of(), 0);

        List<Chunk> result = service.hydrate(List.of(chunk));

        assertThat(result).containsExactly(chunk);
    }

    @Test
    @DisplayName("Passes a chunk through unchanged when its chunkId isn't found in Mongo")
    void passthroughWhenChunkIdNotFound() {
        Chunk chunk = new Chunk("PDF", "content", Map.of("chunkId", "missing"), 0);
        when(repository.findByIds(anyCollection())).thenReturn(Map.of());

        List<Chunk> result = service.hydrate(List.of(chunk));

        assertThat(result).containsExactly(chunk);
    }

    @Test
    @DisplayName("Fails open (passes chunks through unchanged) when Mongo lookup throws")
    void failsOpenOnMongoError() {
        Chunk chunk = new Chunk("PDF", "content", Map.of("chunkId", "abc"), 0);
        when(repository.findByIds(anyCollection())).thenThrow(new RuntimeException("mongo down"));

        List<Chunk> result = service.hydrate(List.of(chunk));

        assertThat(result).containsExactly(chunk);
    }

    @Test
    @DisplayName("Empty input never calls the repository")
    void emptyInputSkipsRepository() {
        List<Chunk> result = service.hydrate(List.of());
        assertThat(result).isEmpty();
    }
}
