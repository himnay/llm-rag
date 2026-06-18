package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple chunker that splits the document into fixed-size chunks.
 * It also supports an optional overlap between chunks.
 */
@Service
public class FixedSizeChunker {

    /**
     * Splits the document into fixed-size chunks with no overlap.
     */
    public List<Chunk> chunk(IngestedDocument document, int chunkSize) {
        return chunk(document, chunkSize, 0);
    }

    /**
     * Splits the document into fixed-size character windows, carrying {@code overlap} characters
     * of the previous window into the next.
     */
    public List<Chunk> chunk(IngestedDocument document, int chunkSize, int overlap) {
        List<Chunk> chunks = new ArrayList<>();

        String content = document.content();
        int start = 0, chunkIndex = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String chunkText = content.substring(start, end);

            Map<String, Object> chunkMetadata = new HashMap<>(document.metadata());
            chunkMetadata.put("chunkIndex", chunkIndex);

            chunks.add(new Chunk(
                    document.source(),
                    chunkText,
                    chunkMetadata,
                    chunkIndex
            ));
            chunkIndex += 1;

            if (end < content.length()) {
                start = end - overlap;
            } else {
                start = end;
            }
        }
        return chunks;
    }

}
