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
 * Level 2 — child of Department; parent of Employees.
 */
@Node("Team")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String purpose;
    private String methodology;

    @Relationship(type = "HAS_MEMBER", direction = Relationship.Direction.OUTGOING)
    private List<Employee> members = new ArrayList<>();

    public Team(String name, String purpose, String methodology) {
        this.name = name;
        this.purpose = purpose;
        this.methodology = methodology;
    }
}
