package com.org.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Provides a deterministic, offline {@link EmbeddingModel} for tests so the vector store works
 * without calling OpenAI. Vectors are seeded from the input text hash, so equal text yields equal
 * vectors (good enough for storage + similarity-search smoke tests; not for semantic quality).
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeEmbeddingConfig {

    @Bean
    @Primary
    EmbeddingModel embeddingModel() {
        return new FakeEmbeddingModel();
    }

    static final class FakeEmbeddingModel implements EmbeddingModel {

        private static final int DIM = 1536;

        private static float[] vectorFor(String text) {
            Random random = new Random(text == null ? 0 : text.hashCode());
            float[] vector = new float[DIM];
            double norm = 0.0;
            for (int i = 0; i < DIM; i++) {
                vector[i] = (float) (random.nextDouble() * 2 - 1);
                norm += vector[i] * vector[i];
            }
            norm = Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < DIM; i++) {
                    vector[i] /= (float) norm;
                }
            }
            return vector;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            List<String> instructions = request.getInstructions();
            for (int i = 0; i < instructions.size(); i++) {
                embeddings.add(new Embedding(vectorFor(instructions.get(i)), i));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vectorFor(document.getText());
        }

        @Override
        public int dimensions() {
            return DIM;
        }
    }
}
