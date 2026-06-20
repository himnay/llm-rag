package com.org.retrieval;

import com.org.chunking.model.Chunk;
import com.org.common.Resilience;
import com.org.mongo.ChunkDocument;
import com.org.mongo.ChunkDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Replaces each candidate chunk's content with the full text stored in MongoDB (the system of
 * record for chunk text), looked up by the {@code chunkId} carried in its metadata — OpenSearch is
 * only the search index and its copy of the text is not relied upon.
 *
 * <p>Fails open: chunks with no {@code chunkId}, or whose id isn't found in Mongo (e.g. ingested
 * before this feature shipped, or a Mongo outage), pass through unchanged rather than being
 * dropped, so retrieval never goes empty due to a hydration gap.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkHydrationService {

    private final ChunkDocumentRepository chunkDocumentRepository;

    public List<Chunk> hydrate(List<Chunk> chunks) {
        List<String> chunkIds = chunks.stream()
                .map(c -> Objects.toString(c.metadata().get("chunkId"), null))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            log.warn("No chunkId present on any candidate chunk; skipping Mongo hydration");
            return chunks;
        }

        Map<String, ChunkDocument> byId;
        try {
            byId = Resilience.withRetry("mongo hydrate findByIds", 3, 100L,
                    () -> chunkDocumentRepository.findByIds(chunkIds));
        } catch (Exception e) {
            log.warn("Mongo hydration failed ({}); falling back to search-index text", e.getMessage());
            return chunks;
        }

        List<Chunk> hydrated = new ArrayList<>(chunks.size());
        for (Chunk chunk : chunks) {
            String chunkId = Objects.toString(chunk.metadata().get("chunkId"), null);
            ChunkDocument doc = chunkId == null ? null : byId.get(chunkId);
            if (doc == null) {
                hydrated.add(chunk);
                continue;
            }
            Map<String, Object> mergedMetadata = new HashMap<>(
                    doc.getMetadata() != null ? doc.getMetadata() : Map.of());
            mergedMetadata.putAll(chunk.metadata()); // retrieval-time metadata (e.g. score) wins
            hydrated.add(new Chunk(chunk.source(), doc.getContent(), mergedMetadata, chunk.chunkIndex()));
        }
        return hydrated;
    }
}
