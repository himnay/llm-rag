package com.org.enrichment;

import com.org.chunking.model.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.ai.model.transformer.SummaryMetadataEnricher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Optionally enriches chunk metadata with LLM-derived {@code excerpt_keywords} and
 * {@code section_summary} using Spring AI's metadata enrichers. No-op unless
 * {@code app.enrichment.enabled=true}.
 */
@Slf4j
@Component
public class ChunkEnricher {

    private final EnrichmentProperties properties;
    private final KeywordMetadataEnricher keywordEnricher;
    private final SummaryMetadataEnricher summaryEnricher;

    /**
     * Builds the keyword/summary enrichers from the available {@link ChatModel}, if any. Both
     * enrichers stay {@code null} when no chat model is configured, making {@link #enrich} a no-op.
     */
    public ChunkEnricher(EnrichmentProperties properties, ObjectProvider<ChatModel> chatModelProvider) {
        this.properties = properties;
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        this.keywordEnricher = chatModel != null
                ? new KeywordMetadataEnricher(chatModel, properties.getKeywordCount()) : null;
        this.summaryEnricher = chatModel != null
                ? new SummaryMetadataEnricher(chatModel, List.of(SummaryMetadataEnricher.SummaryType.CURRENT)) : null;
    }

    /**
     * Adds LLM-derived {@code excerpt_keywords} and/or {@code section_summary} metadata to each
     * chunk. Returns the input list unchanged if enrichment is disabled, the input is empty, no
     * chat model is available, or the underlying LLM call fails.
     *
     * @param chunks chunks to enrich, in order
     * @return the same chunks with enriched metadata, or the original list on no-op/failure
     */
    public List<Chunk> enrich(List<Chunk> chunks) {
        if (!properties.isEnabled() || chunks.isEmpty() || keywordEnricher == null) {
            return chunks;
        }

        List<Document> documents = chunks.stream()
                .map(c -> new Document(c.content(), new java.util.HashMap<>(c.metadata())))
                .toList();

        try {
            if (properties.isKeywords()) {
                documents = keywordEnricher.apply(documents);
            }
            if (properties.isSummary()) {
                documents = summaryEnricher.apply(documents);
            }
        } catch (Exception e) {
            log.warn("Enrichment failed ({}); storing chunks without enriched metadata", e.getMessage());
            return chunks;
        }

        List<Chunk> enriched = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk original = chunks.get(i);
            enriched.add(new Chunk(original.source(), original.content(),
                    documents.get(i).getMetadata(), original.chunkIndex()));
        }
        log.info("Enriched {} chunk(s) (keywords={}, summary={})",
                enriched.size(), properties.isKeywords(), properties.isSummary());
        return enriched;
    }
}
