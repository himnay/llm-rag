package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.chunking.strategy.ChunkingProperties;
import com.org.chunking.strategy.ChunkingStrategyClassifier;
import com.org.chunking.strategy.ChunkingStrategyFactory;
import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Routes a document to a chunking strategy. When {@code app.chunking.strategy} is a concrete name it
 * is used for every source; otherwise ({@code auto}), DB sources always use whole-row chunking
 * (structural, never classifier-selected), and non-DB sources are optionally routed via
 * {@link ChunkingStrategyClassifier} (when {@code app.chunking.classifier.enabled=true}), falling
 * back to per-source defaults (PDF→recursive, WIKI→markdown, anything else→token) when the
 * classifier is disabled, fails, or returns an unusable answer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingOrchestrator {

    private final ChunkingStrategyFactory strategyFactory;
    private final ChunkingProperties properties;
    private final DatabaseChunker databaseChunker;
    private final ChunkingStrategyClassifier classifier;

    /**
     * Chunks the document using the configured strategy, or a per-source default when
     * {@code app.chunking.strategy=auto}.
     */
    public List<Chunk> chunk(IngestedDocument document) {
        String configured = properties.getStrategy();
        if (configured != null && !configured.isBlank() && !"auto".equalsIgnoreCase(configured)) {
            return strategyFactory.get(configured).chunk(document);
        }
        if ("DB".equals(document.source())) {
            return databaseChunker.chunk(document);
        }
        if (properties.getClassifier().isEnabled()) {
            Optional<String> classified = classifier.classify(document);
            if (classified.isPresent() && strategyFactory.has(classified.get())) {
                log.debug("Chunking classifier selected '{}' for source='{}'", classified.get(), document.source());
                return strategyFactory.get(classified.get()).chunk(document);
            }
        }
        return switch (document.source()) {
            case "WIKI" -> strategyFactory.get("markdown").chunk(document);
            case "PDF" -> strategyFactory.get("recursive").chunk(document);
            default -> strategyFactory.get("token").chunk(document);
        };
    }
}
