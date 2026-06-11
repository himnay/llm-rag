package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Drops chunks whose content length falls outside the configured band — too short to be useful or
 * too long for the LLM's context budget. Disabled when both bounds are 0.
 */
@Component
@RequiredArgsConstructor
public class LengthFilter implements RetrievalPostProcessor {

    private final RetrievalProperties properties;

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public List<Chunk> process(String query, List<Chunk> chunks) {
        int min = properties.getLength().getMinChars();
        int max = properties.getLength().getMaxChars();
        if (min <= 0 && max <= 0) {
            return chunks;
        }
        return chunks.stream()
                .filter(c -> {
                    int len = c.content() == null ? 0 : c.content().length();
                    return (min <= 0 || len >= min) && (max <= 0 || len <= max);
                })
                .collect(Collectors.toList());
    }
}
