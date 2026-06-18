package com.org.llm.dto;

import java.util.List;

/**
 * D3.js-compatible graph export payload.
 * Use {@code GET /api/graph/export?format=json} to retrieve it.
 */
public record GraphExportDto(List<GraphNode> nodes, List<GraphLink> links) {

    public record GraphNode(Long id, String label, String name) {
    }

    public record GraphLink(Long source, Long target, String type) {
    }
}
