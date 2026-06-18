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
 * Level 0 — root node of the 4-level hierarchy:
 * Company → Department → Team → Employee
 */
@Node("Company")
@Getter
@Setter
@NoArgsConstructor
public class Company {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String industry;
    private String description;
    private String founded;
    private String headquarters;

    @Relationship(type = "HAS_DEPARTMENT", direction = Relationship.Direction.OUTGOING)
    private List<Department> departments = new ArrayList<>();

    /**
     * Creates a company node with the given attributes (no relationships set).
     */
    public Company(String name, String industry, String description, String founded, String headquarters) {
        this.name = name;
        this.industry = industry;
        this.description = description;
        this.founded = founded;
        this.headquarters = headquarters;
    }
}
