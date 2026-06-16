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
 * Level 3 — child of Team.
 * Employees form a management chain (REPORTS_TO) and contribute to Projects (WORKS_ON).
 * This node is the richest in cross-cutting edges, making it the best entry point for RAG traversal.
 */
@Node("Employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String title;
    private String email;
    private String bio;
    private List<String> skills;
    private int yearsExperience;

    /**
     * Management chain — forms an additional hierarchy on top of the Team structure.
     */
    @Relationship(type = "REPORTS_TO", direction = Relationship.Direction.OUTGOING)
    private Employee manager;

    /**
     * Cross-hierarchy edges: employees work on projects owned by departments.
     */
    @Relationship(type = "WORKS_ON", direction = Relationship.Direction.OUTGOING)
    private List<WorksOnRelationship> projectAssignments = new ArrayList<>();

    public Employee(String name, String title, String email, String bio,
                    List<String> skills, int yearsExperience) {
        this.name = name;
        this.title = title;
        this.email = email;
        this.bio = bio;
        this.skills = skills;
        this.yearsExperience = yearsExperience;
    }
}
