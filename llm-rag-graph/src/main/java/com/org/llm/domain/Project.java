package com.org.llm.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Cross-cutting node: owned by a Department, worked on by Employees,
 * and uses specific Technologies. Creates horizontal edges across the hierarchy.
 */
@Node("Project")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String description;
    private String status;      // active | completed | paused
    private String startDate;
    private String goal;

    @Relationship(type = "USES_TECHNOLOGY", direction = Relationship.Direction.OUTGOING)
    private List<Technology> technologies = new ArrayList<>();

    /**
     * Creates a project node with the given attributes (no relationships set).
     */
    public Project(String name, String description, String status, String startDate, String goal) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.startDate = startDate;
        this.goal = goal;
    }
}
