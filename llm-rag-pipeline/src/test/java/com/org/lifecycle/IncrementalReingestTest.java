package com.org.lifecycle;

import com.org.config.OpenSearchProperties;
import com.org.ingestion.model.IngestedDocument;
import com.org.lifecycle.model.KnowledgeRequest;
import com.org.lifecycle.model.SourceType;
import com.org.mongo.ChunkDocumentRepository;
import com.org.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-ingesting a source must only touch what changed: unchanged chunks keep their vectors, edited
 * chunks are replaced (not duplicated), chunks the source no longer has are removed, and a source
 * that was deleted comes back in full when it is ingested again.
 */
class IncrementalReingestTest extends IntegrationTest {

    @Autowired
    private KnowledgeLifecycleService lifecycle;

    @Autowired
    private ChunkDocumentRepository chunkDocuments;

    @Autowired
    private OpenSearchClient openSearch;

    @Autowired
    private OpenSearchProperties openSearchProperties;

    @Test
    @DisplayName("A partial edit re-stores only the edited chunk and drops the removed one")
    void partialChangeKeepsUnchangedChunks() throws IOException {
        String name = "reingest-" + UUID.randomUUID();
        String identity = "WIKI#" + name;

        lifecycle.ingestDocuments(List.of(wiki(identity, "# Alpha\nfirst section", "# Beta\nsecond section", "# Gamma\nthird section")));
        Map<String, String> before = chunkDocuments.contentHashesByIdentity(identity);
        assertThat(before).hasSize(3);
        assertThat(vectorCount(identity)).isEqualTo(3);

        lifecycle.ingestDocuments(List.of(wiki(identity, "# Alpha\nfirst section", "# Beta\nsecond section, edited")));
        Map<String, String> after = chunkDocuments.contentHashesByIdentity(identity);

        assertThat(after).hasSize(2);
        assertThat(vectorCount(identity)).as("edited chunk replaced in place, removed chunk deleted").isEqualTo(2);
        long unchanged = after.entrySet().stream().filter(e -> e.getValue().equals(before.get(e.getKey()))).count();
        assertThat(unchanged).as("the untouched Alpha section is kept as-is").isEqualTo(1);
    }

    @Test
    @DisplayName("A deleted source is fully re-indexed when it is ingested again")
    void deleteThenReingestRestoresEverything() throws IOException {
        String name = "reingest-" + UUID.randomUUID();
        String identity = "WIKI#" + name;
        IngestedDocument page = wiki(identity, "# One\nfirst", "# Two\nsecond");

        lifecycle.ingestDocuments(List.of(page));
        assertThat(vectorCount(identity)).isEqualTo(2);

        lifecycle.delete(KnowledgeRequest.builder().sourceType(SourceType.WIKI).name(name).build());
        assertThat(chunkDocuments.contentHashesByIdentity(identity)).isEmpty();
        assertThat(vectorCount(identity)).isZero();

        lifecycle.ingestDocuments(List.of(page));
        assertThat(chunkDocuments.contentHashesByIdentity(identity)).hasSize(2);
        assertThat(vectorCount(identity)).isEqualTo(2);
    }

    private static IngestedDocument wiki(String identity, String... sections) {
        return new IngestedDocument("WIKI", String.join("\n", sections), Map.of("identity", identity));
    }

    private long vectorCount(String identity) throws IOException {
        String index = openSearchProperties.getIndexName();
        openSearch.indices().refresh(r -> r.index(index));
        return openSearch.count(c -> c.index(index)
                .query(q -> q.term(t -> t.field("metadata.identity.keyword").value(FieldValue.of(identity))))).count();
    }
}
