package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * One stage in the retrieval post-processing chain (filter / rank / diversify / rerank). Stages are
 * Spring beans applied in {@link Ordered} order by {@code RetrievalService}, so each technique is
 * independently testable and toggleable instead of being inlined into the service.
 *
 * <p>The similarity (or rerank) score for a chunk is carried in metadata under {@link #SCORE_KEY}.</p>
 */
public interface RetrievalPostProcessor extends Ordered {

    String SCORE_KEY = "score";

    List<Chunk> process(String query, List<Chunk> chunks);

    static double score(Chunk chunk) {
        Object v = chunk.metadata().get(SCORE_KEY);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
