package com.org.chunking;

import com.org.chunking.model.Chunk;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Objects;

/**
 * Deterministic chunkId, computed at write/read time rather than stored on {@link Chunk} itself
 * (avoids threading a new field through every chunker). Two chunks with the same identity, source
 * and chunkIndex always resolve to the same id, so re-ingesting unchanged content upserts the same
 * Mongo/OpenSearch records instead of accumulating duplicates.
 */
public final class ChunkIdGenerator {

    private ChunkIdGenerator() {
    }

    public static String idFor(Chunk chunk) {
        return idFor(Objects.toString(chunk.metadata().get("identity"), ""), chunk.source(), chunk.chunkIndex());
    }

    public static String idFor(String identity, String source, int chunkIndex) {
        return DigestUtils.sha256Hex(identity + "|" + source + "|" + chunkIndex);
    }
}
