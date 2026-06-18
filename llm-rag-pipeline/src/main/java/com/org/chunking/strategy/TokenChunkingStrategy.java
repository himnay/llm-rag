package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Token-aware splitting using Spring AI's {@link TokenTextSplitter} (chunk size in tokens).
 */
@Component
@RequiredArgsConstructor
public class TokenChunkingStrategy extends AbstractChunkingStrategy {

    private final ChunkingProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "token";
    }

    /**
     * Splits the document into chunks sized by token count, using Spring AI's
     * {@code TokenTextSplitter} configured with {@code app.chunking.token.chunk-size}.
     */
    @Override
    public List<Chunk> chunk(IngestedDocument document) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(properties.getToken().getChunkSize())
                .build();
        List<Document> split = splitter.split(new Document(document.content(), Map.of()));
        return toChunks(document, split.stream().map(Document::getText).toList());
    }
}
