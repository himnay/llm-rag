package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maximal Marginal Relevance re-ordering: greedily picks the candidate maximizing
 * {@code lambda * relevance - (1 - lambda) * maxSimilarityToAlreadyPicked}, reducing redundancy
 * among the returned chunks. Similarity is the embedding-free text cosine ({@link TextSimilarity}).
 * Opt-in via {@code app.retrieval.mmr.enabled}.
 */
@Component
@RequiredArgsConstructor
public class MmrDiversityProcessor implements RetrievalPostProcessor {

    private final RetrievalProperties properties;

    @Override
    public int getOrder() {
        return 60;
    }

    @Override
    public List<Chunk> process(String query, List<Chunk> chunks) {
        if (!properties.getMmr().isEnabled() || chunks.size() < 3) {
            return chunks;
        }
        double lambda = properties.getMmr().getLambda();
        List<Chunk> candidates = new ArrayList<>(chunks);
        List<Chunk> selected = new ArrayList<>(candidates.size());

        while (!candidates.isEmpty()) {
            Chunk best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Chunk candidate : candidates) {
                double relevance = RetrievalPostProcessor.score(candidate);
                double maxSim = 0.0;
                for (Chunk picked : selected) {
                    maxSim = Math.max(maxSim, TextSimilarity.cosine(candidate.content(), picked.content()));
                }
                double mmr = lambda * relevance - (1 - lambda) * maxSim;
                if (mmr > bestScore) {
                    bestScore = mmr;
                    best = candidate;
                }
            }
            selected.add(best);
            candidates.remove(best);
        }
        return selected;
    }
}
