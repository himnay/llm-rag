package com.org.llm.dto;

public record GraphStats(
        long companies,
        long departments,
        long teams,
        long employees,
        long projects,
        long technologies,
        long totalNodes,
        long totalRelationships
) {
}
