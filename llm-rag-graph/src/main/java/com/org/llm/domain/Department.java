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
 * Level 1 — child of Company; parent of Team and owner of Projects.
 * Departments also form a collaboration network between themselves.
 */
@Node("Department")
@Getter @Setter @NoArgsConstructor
public class Department {

    @Id @GeneratedValue
    private Long id;

    private String name;
    private String focus;
    private String description;
    private int headcount;

    @Relationship(type = "HAS_TEAM", direction = Relationship.Direction.OUTGOING)
    private List<Team> teams = new ArrayList<>();

    @Relationship(type = "COLLABORATES_WITH", direction = Relationship.Direction.OUTGOING)
    private List<Department> collaborators = new ArrayList<>();

    @Relationship(type = "OWNS_PROJECT", direction = Relationship.Direction.OUTGOING)
    private List<Project> projects = new ArrayList<>();

    public Department(String name, String focus, String description, int headcount) {
        this.name = name;
        this.focus = focus;
        this.description = description;
        this.headcount = headcount;
    }
}
