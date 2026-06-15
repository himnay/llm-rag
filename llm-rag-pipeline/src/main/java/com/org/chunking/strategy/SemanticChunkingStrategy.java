package com.org.chunking.strategy;

import com.org.cache.EmbeddingCacheService;
import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding-based semantic chunking: splits text into sentences, embeds them, and starts a new
 * chunk whenever the cosine similarity between consecutive sentences drops below a threshold
 * (or the running chunk exceeds a character cap). Keeps semantically-coherent passages together.
 * Uses {@link EmbeddingCacheService} to avoid re-embedding identical sentences across runs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticChunkingStrategy extends AbstractChunkingStrategy {

    private final ChunkingProperties properties;
    private final EmbeddingCacheService embeddingCacheService;

    @Override
    public String name() {
        return "semantic";
    }

    @Override
    public List<Chunk> chunk(IngestedDocument document) {
        List<String> sentences = splitSentences(document.content());
        if (sentences.size() <= 1) {
            return toChunks(document, sentences);
        }

        List<float[]> embeddings = embeddingCacheService.embed(sentences);
        double threshold = properties.getSemantic().getThreshold();
        int maxChars = properties.getSemantic().getMaxChars();

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder(sentences.get(0));
        for (int i = 1; i < sentences.size(); i++) {
            double similarity = cosine(embeddings.get(i - 1), embeddings.get(i));
            boolean boundary = similarity < threshold
                    || current.length() + sentences.get(i).length() > maxChars;
            if (boundary) {
                chunks.add(current.toString());
                current = new StringBuilder(sentences.get(i));
            } else {
                current.append(' ').append(sentences.get(i));
            }
        }
        chunks.add(current.toString());
        return toChunks(document, chunks);
    }

    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String s : text.split("(?<=[.!?])\\s+|\\n{2,}")) {
            if (!s.isBlank()) {
                sentences.add(s.strip());
            }
        }
        return sentences;
    }

    private static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
