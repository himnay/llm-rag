package com.org.lifecycle;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Persists the content hash of each ingested source (keyed by identity) so the lifecycle service
 * can skip re-embedding unchanged documents and replace only those whose content actually changed.
 */
@Repository
@RequiredArgsConstructor
public class IngestionLogRepository {

    private final JdbcTemplate jdbc;

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * True when an entry with this identity already records the same content hash.
     */
    public boolean isUnchanged(String identity, String contentHash) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ingestion_log WHERE identity = ? AND content_hash = ?",
                Integer.class, identity, contentHash);
        return count != null && count > 0;
    }

    /**
     * Inserts or updates the hash/chunk-count for an identity.
     */
    public void upsert(String identity, String contentHash, int chunkCount) {
        jdbc.update("""
                INSERT INTO ingestion_log (identity, content_hash, chunk_count, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (identity)
                DO UPDATE SET content_hash = EXCLUDED.content_hash,
                              chunk_count  = EXCLUDED.chunk_count,
                              updated_at   = NOW()
                """, identity, contentHash, chunkCount);
    }

    public void deleteByIdentity(String identity) {
        jdbc.update("DELETE FROM ingestion_log WHERE identity = ?", identity);
    }

    public void deleteAll() {
        jdbc.update("TRUNCATE TABLE ingestion_log");
    }
}
