package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WikiSemanticChunker {

    /**
     * Splits a wiki document into one chunk per markdown heading section.
     */
    public List<Chunk> chunk(IngestedDocument document) {
        List<Chunk> chunks = new ArrayList<>();

        String content = document.content();

        // Split by Markdown headings (##, ###, etc.)
        String[] sections = content.split("\n(?=#+\\s)");

        int chunkIndex = 0;

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Map<String, Object> chunkMetadata = new HashMap<>(document.metadata());
            chunkMetadata.put("chunkIndex", chunkIndex);
            chunkMetadata.put("chunkType", "WIKI_SECTION");

            chunks.add(new Chunk(
                    document.source(),
                    trimmed,
                    chunkMetadata,
                    chunkIndex
            ));
            chunkIndex++;
        }

        return chunks;
    }
}
