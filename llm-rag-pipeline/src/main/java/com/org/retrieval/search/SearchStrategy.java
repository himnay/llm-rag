package com.org.retrieval.search;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * GoF <b>Strategy</b> for first-stage candidate search (the interchangeable "how do we fetch
 * candidates" step that {@code RetrievalService} delegates to). Implementations return documents
 * best-first with a 0..1 relevance in {@link Document#getScore()}; the post-processing chain and
 * reranking run on top, whichever strategy produced the candidates.
 */
public interface SearchStrategy {

    /** Which {@code app.retrieval.search.mode} value selects this implementation. */
    SearchMode mode();

    List<Document> search(String query, int topK);
}
