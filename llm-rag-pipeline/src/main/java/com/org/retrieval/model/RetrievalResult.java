package com.org.retrieval.model;

import com.org.chunking.model.Chunk;

import java.util.List;

/**
 * Retrieval response: the ranked chunks plus a de-duplicated list of {@link Citation}s describing
 * where each piece of context came from.
 */
public record RetrievalResult(List<Chunk> chunks, List<Citation> citations) {
}
