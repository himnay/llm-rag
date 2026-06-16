package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bi-encoder reranking: re-embeds the query and each candidate with the local
 * {@link EmbeddingModel} and re-scores by exact cosine similarity. Useful when the vector store's
 * ANN search (or a hybrid first stage) returns approximate scores, or when candidates were merged
 * from sources with incomparable scores. One batched embedding call — no extra vendor needed.
 */
@Component
@RequiredArgsConstructor
public class BiEncoderReranker implements Reranker {

    private final EmbeddingModel embeddingModel;

    private static double cosine(float[] a, float[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.BI_ENCODER;
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        float[] queryVector = embeddingModel.embed(query);
        List<float[]> docVectors = embeddingModel.embed(chunks.stream().map(Chunk::content).toList());

        List<Chunk> reranked = new ArrayList<>(chunks);
        for (int i = 0; i < reranked.size(); i++) {
            Reranker.score(reranked.get(i), cosine(queryVector, docVectors.get(i)));
        }
        reranked.sort(Comparator.comparingDouble(
                (Chunk c) -> com.org.retrieval.postprocess.RetrievalPostProcessor.score(c)).reversed());
        return reranked;
    }
}
