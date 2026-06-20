package com.org.mongo;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stores/reads {@link ChunkDocument}s in the {@code chunks} collection, keyed by chunkId. Uses
 * {@link MongoTemplate} directly (imperative queries) rather than a Spring Data repository
 * interface, matching {@code IngestionLogRepository}'s raw-{@code JdbcTemplate} style.
 */
@Repository
@RequiredArgsConstructor
public class ChunkDocumentRepository {

    private static final String COLLECTION = "chunks";

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    void init() {
        mongoTemplate.indexOps(COLLECTION).ensureIndex(new Index("identity", Sort.Direction.ASC));
    }

    /**
     * Upserts a batch of chunk documents in one round-trip.
     */
    public void upsertAll(List<ChunkDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, COLLECTION);
        for (ChunkDocument doc : docs) {
            Query query = Query.query(Criteria.where("_id").is(doc.getChunkId()));
            Update update = new Update()
                    .set("identity", doc.getIdentity())
                    .set("source", doc.getSource())
                    .set("chunkIndex", doc.getChunkIndex())
                    .set("content", doc.getContent())
                    .set("metadata", doc.getMetadata())
                    .set("updatedAt", Instant.now())
                    .setOnInsert("createdAt", Instant.now());
            bulk.upsert(query, update);
        }
        bulk.execute();
    }

    public Optional<ChunkDocument> findById(String chunkId) {
        return Optional.ofNullable(mongoTemplate.findById(chunkId, ChunkDocument.class, COLLECTION));
    }

    /**
     * Batched lookup, keyed by chunkId, for retrieval-time hydration.
     */
    public Map<String, ChunkDocument> findByIds(Collection<String> chunkIds) {
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where("_id").in(chunkIds));
        List<ChunkDocument> found = mongoTemplate.find(query, ChunkDocument.class, COLLECTION);
        return found.stream().collect(Collectors.toMap(ChunkDocument::getChunkId, Function.identity()));
    }

    public void deleteByIdentity(String identity) {
        mongoTemplate.remove(Query.query(Criteria.where("identity").is(identity)), COLLECTION);
    }

    public void deleteAll() {
        mongoTemplate.remove(new Query(), COLLECTION);
    }
}
