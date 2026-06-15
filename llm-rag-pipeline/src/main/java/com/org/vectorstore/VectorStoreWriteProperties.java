package com.org.vectorstore;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vectorstore.write")
public class VectorStoreWriteProperties {

    private int batchSize = 50;
    private int concurrency = 4;
    private int queueCapacity = 64;

    /** Embedding model ID stamped into every chunk's metadata at write time. Changing this
     *  after initial ingestion is a signal that the full corpus must be re-embedded. */
    private String embeddingModelId = "text-embedding-3-small";

    /** Dimension of the embedding vectors; must match the model above. */
    private int embeddingDimension = 1536;

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getConcurrency() { return concurrency; }
    public void setConcurrency(int concurrency) { this.concurrency = concurrency; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public String getEmbeddingModelId() { return embeddingModelId; }
    public void setEmbeddingModelId(String embeddingModelId) { this.embeddingModelId = embeddingModelId; }
    public int getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(int embeddingDimension) { this.embeddingDimension = embeddingDimension; }
}
