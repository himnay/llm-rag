package com.org.llm.service;

import java.util.List;

/**
 * Strategy interface for graph context extraction. Different strategies apply different
 * traversal approaches (keyword-match, path-traversal, full-text) and can be selected
 * per-request or combined.
 */
public interface GraphExtractionStrategy {

    String name();

    /**
     * Extract context lines from the graph for the given keywords.
     *
     * @param keywords pre-filtered keywords extracted from the user question
     * @return natural-language sentences describing matched graph entities and relationships
     */
    List<String> extract(List<String> keywords);
}
