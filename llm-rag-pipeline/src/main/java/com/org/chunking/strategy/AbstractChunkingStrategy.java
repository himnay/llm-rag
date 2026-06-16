package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared helper that turns text segments into {@link Chunk}s with consistent metadata.
 */
public abstract class AbstractChunkingStrategy implements ChunkingStrategy {

    /**
     * Builds chunks from ordered text segments, skipping blanks and assigning chunk indexes.
     */
    protected List<Chunk> toChunks(IngestedDocument document, List<String> segments) {
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new HashMap<>(document.metadata());
            metadata.put("source", document.source());
            metadata.put("chunkIndex", index);
            metadata.put("chunkStrategy", name());
            chunks.add(new Chunk(document.source(), segment.strip(), metadata, index));
            index++;
        }
        return chunks;
    }
}
