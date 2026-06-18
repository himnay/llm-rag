package com.org.llm.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Rich relationship between Employee and Project — carries the role the employee plays.
 */
@RelationshipProperties
@Getter
@Setter
@NoArgsConstructor
public class WorksOnRelationship {

    @RelationshipId
    private Long id;

    private String role;        // lead | contributor | reviewer | architect
    private String since;
    private int allocationPct;  // 0–100

    @TargetNode
    private Project project;

    /**
     * Creates a WORKS_ON relationship with the given role, start date, allocation, and target project.
     */
    public WorksOnRelationship(String role, String since, int allocationPct, Project project) {
        this.role = role;
        this.since = since;
        this.allocationPct = allocationPct;
        this.project = project;
    }
}
