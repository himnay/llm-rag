-- Tracks the content hash of each ingested knowledge source (by identity) so re-ingesting an
-- UNCHANGED document can be skipped (no re-embedding cost) while a CHANGED document is replaced.
CREATE TABLE ingestion_log (
    identity     VARCHAR(512) PRIMARY KEY,   -- e.g. PDF#HR_Leave_Policy.pdf, DB#faqs
    content_hash VARCHAR(64)  NOT NULL,       -- SHA-256 hex of the normalised content
    chunk_count  INTEGER      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
