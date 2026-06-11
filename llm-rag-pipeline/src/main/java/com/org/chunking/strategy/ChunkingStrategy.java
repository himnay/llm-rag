package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;

import java.util.List;

/** A pluggable text-chunking algorithm. Implementations are Spring beans discovered by name. */
public interface ChunkingStrategy {

    /** Lower-case strategy id used for selection (e.g. {@code recursive}, {@code token}). */
    String name();

    /** Splits a (already cleaned) document into chunks. */
    List<Chunk> chunk(IngestedDocument document);
}
