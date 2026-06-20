package com.org.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Cleanup for the (now vestigial) {@code ingestion_log} table — dedup itself has moved to
 * chunk-level Redis hashing ({@code ChunkDedupService}), but deletes still clear this table for
 * audit-data hygiene parity with the Mongo/OpenSearch deletes.
 */
@Repository
@RequiredArgsConstructor
public class IngestionLogRepository {

    private final JdbcTemplate jdbc;

    /**
     * Removes the log entry for an identity (called when its source is deleted).
     */
    public void deleteByIdentity(String identity) {
        jdbc.update("DELETE FROM ingestion_log WHERE identity = ?", identity);
    }

    /**
     * Clears all ingestion log entries.
     */
    public void deleteAll() {
        jdbc.update("TRUNCATE TABLE ingestion_log");
    }
}
