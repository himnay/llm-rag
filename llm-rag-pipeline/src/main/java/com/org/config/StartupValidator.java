package com.org.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Fail-fast / warn-loud startup checks:
 * <ul>
 *   <li>Warns when {@code OPENAI_API_KEY} is unset or still the dev placeholder (embeddings will fail).</li>
 *   <li>Asserts the configured OpenSearch index dimension matches the embedding model's actual
 *       dimension — a mismatch silently breaks kNN search, so we fail startup instead.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupValidator implements ApplicationRunner {

    private final EmbeddingModel embeddingModel;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.vectorstore.opensearch.dimensions:0}")
    private int configuredDimensions;

    @Override
    public void run(ApplicationArguments args) {
        if (openAiApiKey == null || openAiApiKey.isBlank() || openAiApiKey.startsWith("sk-placeholder")) {
            log.warn("CONFIG | OPENAI_API_KEY is unset or a placeholder — embeddings will fail at runtime. "
                    + "Set a real key (no default is provided in the prod profile).");
        }
        try {
            int actual = embeddingModel.dimensions();
            if (configuredDimensions > 0 && actual != configuredDimensions) {
                throw new IllegalStateException("Embedding dimension mismatch: model reports " + actual
                        + " but spring.ai.vectorstore.opensearch.dimensions=" + configuredDimensions
                        + ". Update the index dimension to match the embedding model.");
            }
            log.info("CONFIG | embedding dimension {} matches the OpenSearch index configuration", actual);
        } catch (IllegalStateException mismatch) {
            throw mismatch;
        } catch (Exception e) {
            log.warn("CONFIG | could not verify embedding dimension ({}) — skipping check", e.getMessage());
        }
    }
}
