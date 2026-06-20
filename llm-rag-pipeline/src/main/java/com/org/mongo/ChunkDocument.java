package com.org.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.util.Map;

/**
 * One chunk's full text + descriptive metadata, keyed by its deterministic chunkId — the system
 * of record for chunk content. OpenSearch only carries a copy of the text alongside the vector;
 * retrieval hydrates from here by chunkId rather than relying on that copy.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkDocument {

    @Id
    private String chunkId;
    private String identity;
    private String source;
    private int chunkIndex;
    private String content;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
