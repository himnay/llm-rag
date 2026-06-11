package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import org.springframework.stereotype.Component;

import java.util.List;

/** Splits markdown by heading boundaries ({@code #}, {@code ##}, ...), keeping each section intact. */
@Component
public class MarkdownSectionChunkingStrategy extends AbstractChunkingStrategy {

    @Override
    public String name() {
        return "markdown";
    }

    @Override
    public List<Chunk> chunk(IngestedDocument document) {
        // Split before any line that starts a markdown heading.
        List<String> sections = List.of(document.content().split("\n(?=#+\\s)"));
        return toChunks(document, sections);
    }
}
