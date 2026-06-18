package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.chunking.strategy.ChunkingProperties;
import com.org.chunking.strategy.ChunkingStrategyFactory;
import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Routes a document to a chunking strategy. When {@code app.chunking.strategy} is a concrete name it
 * is used for every source; otherwise ({@code auto}) per-source defaults apply:
 * PDF→recursive, WIKI→markdown, DB→whole-row, anything else→token.
 */
@Service
@RequiredArgsConstructor
public class ChunkingOrchestrator {

    private final ChunkingStrategyFactory strategyFactory;
    private final ChunkingProperties properties;
    private final DatabaseChunker databaseChunker;

    /**
     * Chunks the document using the configured strategy, or a per-source default when
     * {@code app.chunking.strategy=auto}.
     */
    public List<Chunk> chunk(IngestedDocument document) {
        String configured = properties.getStrategy();
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured)) {
            return strategyFactory.get(configured).chunk(document);
        }

        return switch (document.source()) {
            case "DB" -> databaseChunker.chunk(document);
            case "WIKI" -> strategyFactory.get("markdown").chunk(document);
            case "PDF" -> strategyFactory.get("recursive").chunk(document);
            default -> strategyFactory.get("token").chunk(document);
        };
    }
}
