package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Fixed-size character windows with overlap. */
@Component
@RequiredArgsConstructor
public class FixedSizeChunkingStrategy extends AbstractChunkingStrategy {

    private final ChunkingProperties properties;

    @Override
    public String name() {
        return "fixed";
    }

    @Override
    public List<Chunk> chunk(IngestedDocument document) {
        int size = properties.getMaxChars();
        int overlap = Math.min(properties.getOverlap(), size - 1);
        String content = document.content();
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int end = Math.min(start + size, content.length());
            segments.add(content.substring(start, end));
            if (end == content.length()) {
                break;
            }
            start = end - overlap;
        }
        return toChunks(document, segments);
    }
}
