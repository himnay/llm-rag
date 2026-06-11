package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Collapses near-duplicate chunks (e.g. the same passage retrieved from overlapping windows or
 * re-ingested sources). Greedy: keep the highest-scoring chunk and discard later ones whose text
 * cosine similarity to an already-kept chunk exceeds the configured threshold.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NearDuplicateFilter implements RetrievalPostProcessor {

    private final RetrievalProperties properties;

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public List<Chunk> process(String query, List<Chunk> chunks) {
        if (!properties.getDedup().isEnabled() || chunks.size() < 2) {
            return chunks;
        }
        double threshold = properties.getDedup().getThreshold();
        List<Chunk> byScore = new ArrayList<>(chunks);
        byScore.sort(Comparator.comparingDouble(RetrievalPostProcessor::score).reversed());

        List<Chunk> kept = new ArrayList<>();
        for (Chunk candidate : byScore) {
            boolean duplicate = kept.stream().anyMatch(k ->
                    TextSimilarity.cosine(k.content(), candidate.content()) >= threshold);
            if (!duplicate) {
                kept.add(candidate);
            }
        }
        if (kept.size() < chunks.size()) {
            log.debug("Near-duplicate filter collapsed {} → {} chunks", chunks.size(), kept.size());
        }
        return kept;
    }
}
