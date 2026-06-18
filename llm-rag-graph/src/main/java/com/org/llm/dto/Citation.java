package com.org.llm.dto;

/**
 * A first-class provenance pointer back to the Neo4j node (and, where applicable, the relationship
 * traversed to reach it) that backs a line of graph context handed to the LLM. Lets consumers
 * attribute an answer to the specific graph entity it came from, instead of a single opaque
 * context blob.
 */
public record Citation(
        String nodeType,     // Neo4j label, e.g. Employee | Department | Project | Technology
        String nodeId,       // internal Neo4j node id (as string) when known, else null
        String nodeName,     // node.name property
        String relationship
        // relationship traversed to surface this node, e.g. REPORTS_TO | WORKS_ON | HAS_TEAM | COLLABORATES_WITH; null for direct full-text hits
) {
}
