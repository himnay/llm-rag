package com.rag.vectorless.dto;

/**
 * A single retrieval citation backing an answer.
 *
 * @param source     the source filename (matches {@link Chunk#source()})
 * @param chunkIndex the chunk position within the source document, when known
 * @param score      the BM25 relevance score for this chunk, when available
 */
public record Citation(String source, Integer chunkIndex, Double score) {
}
