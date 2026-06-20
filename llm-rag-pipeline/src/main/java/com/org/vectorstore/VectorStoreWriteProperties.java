package com.org.vectorstore;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.vectorstore.write")
public class VectorStoreWriteProperties {

    private int batchSize = 50;
    private int concurrency = 4;
    private int queueCapacity = 64;

    /**
     * Embedding model ID stamped into every chunk's metadata at write time. Changing this
     * after initial ingestion is a signal that the full corpus must be re-embedded.
     */
    private String embeddingModelId = "text-embedding-3-small";

    /**
     * Dimension of the embedding vectors; must match the model above.
     */
    private int embeddingDimension = 1536;

    /**
     * Max input tokens per embedding API call, before the reserve below is subtracted. Default
     * (8191) matches OpenAI's limit — lower this for an embedding vendor with a smaller ceiling so
     * a single call is never rejected for exceeding it.
     */
    private int maxTokensPerBatch = 8191;

    /**
     * Safety margin subtracted from {@link #maxTokensPerBatch} (e.g. 0.1 = use only 90% of the
     * limit) to absorb tokenizer estimation drift between our count and the vendor's.
     */
    private double tokenReservePercentage = 0.1;
}
