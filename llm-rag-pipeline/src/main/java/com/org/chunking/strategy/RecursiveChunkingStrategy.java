package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive character splitter (LangChain-style): tries increasingly fine separators
 * (paragraph → line → space → hard cut), then greedily merges pieces into chunks of at most
 * {@code maxChars} with a character overlap. Keeps natural boundaries where possible.
 */
@Component
@RequiredArgsConstructor
public class RecursiveChunkingStrategy extends AbstractChunkingStrategy {

    private static final String[] SEPARATORS = {"\n\n", "\n", " ", ""};

    private final ChunkingProperties properties;

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return "recursive";
    }

    /**
     * Splits the document by trying paragraph, then line, then space, then hard-cut separators,
     * greedily packing pieces into {@code maxChars}-sized chunks with overlap.
     */
    @Override
    public List<Chunk> chunk(IngestedDocument document) {
        return toChunks(document, split(document.content(), 0, properties.getMaxChars(), properties.getOverlap()));
    }

    private List<String> split(String text, int sepIndex, int maxChars, int overlap) {
        if (text.length() <= maxChars) {
            return new ArrayList<>(List.of(text));
        }
        String separator = SEPARATORS[Math.min(sepIndex, SEPARATORS.length - 1)];
        List<String> parts = separator.isEmpty()
                ? hardSplit(text, maxChars)
                : List.of(text.split(java.util.regex.Pattern.quote(separator), -1));

        List<String> result = new ArrayList<>();
        List<String> buffer = new ArrayList<>();
        for (String part : parts) {
            String piece = separator.isEmpty() ? part : part + separator;
            if (piece.length() > maxChars && sepIndex + 1 < SEPARATORS.length) {
                mergeInto(result, buffer, maxChars, overlap);
                buffer.clear();
                result.addAll(split(piece, sepIndex + 1, maxChars, overlap));
            } else {
                buffer.add(piece);
            }
        }
        mergeInto(result, buffer, maxChars, overlap);
        return result;
    }

    /**
     * Greedily packs pieces into <= maxChars chunks, carrying an overlap tail between chunks.
     */
    private void mergeInto(List<String> out, List<String> pieces, int maxChars, int overlap) {
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() > 0 && current.length() + piece.length() > maxChars) {
                out.add(current.toString());
                String tail = current.length() > overlap
                        ? current.substring(current.length() - overlap) : current.toString();
                current = new StringBuilder(tail);
            }
            current.append(piece);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
    }

    private List<String> hardSplit(String text, int maxChars) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxChars) {
            out.add(text.substring(i, Math.min(i + maxChars, text.length())));
        }
        return out;
    }
}
