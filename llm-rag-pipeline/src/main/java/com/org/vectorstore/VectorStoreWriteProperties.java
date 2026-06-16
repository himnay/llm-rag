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
}
