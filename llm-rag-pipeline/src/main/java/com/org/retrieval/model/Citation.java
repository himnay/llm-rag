package com.org.retrieval.model;

/**
 * A first-class provenance pointer for a retrieved chunk, so consumers can attribute an answer to
 * its source ("HR_Leave_Policy.pdf, page 4") without parsing raw metadata. De-duplicated per source
 * in {@link RetrievalResult}.
 */
public record Citation(
        String source,      // PDF | WIKI | FILE | DB
        String fileName,    // original document name (or DB table)
        String identity,    // stable ingestion identity, e.g. PDF#HR_Leave_Policy.pdf
        Integer page,       // 1-based page for PDFs, else null
        int chunkIndex,
        Double score) {     // similarity / rerank score, when available
}
