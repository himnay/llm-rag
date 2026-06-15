package com.org.generation;

import com.org.chunking.model.Chunk;
import com.org.retrieval.model.Citation;
import com.org.retrieval.model.RetrievalResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts the {@link RetrievalResult} into a formatted context string that is injected into the
 * LLM prompt. Each chunk is numbered and prefixed with its citation header so the model can
 * reference sources in its answer.
 */
@Component
public class ContextBuilder {

    public String build(RetrievalResult result) {
        List<Chunk> chunks = result.chunks();
        List<Citation> citations = result.citations();
        if (chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("Context ").append(i + 1).append(":\n");
            sb.append(citationHeader(citations, i));
            sb.append(chunks.get(i).content()).append("\n\n");
        }
        return sb.toString().strip();
    }

    public boolean isEmpty(RetrievalResult result) {
        return result.chunks().isEmpty();
    }

    private String citationHeader(List<Citation> citations, int chunkIndex) {
        if (chunkIndex >= citations.size()) return "";
        Citation c = citations.get(chunkIndex);
        StringBuilder sb = new StringBuilder("[");
        sb.append(c.source() != null ? c.source() : "SOURCE");
        if (c.fileName() != null && !c.fileName().isBlank()) {
            sb.append(": ").append(c.fileName());
        }
        if (c.page() != null) {
            sb.append(", p.").append(c.page());
        }
        sb.append("]\n");
        return sb.toString();
    }
}
