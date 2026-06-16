package com.org.llm.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Leaf node used by Projects — represents frameworks, languages, and platforms.
 */
@Node("Technology")
@Getter
@Setter
@NoArgsConstructor
public class Technology {

    @Id
    @GeneratedValue
    private Long id;

    private String name;
    private String category;    // language | framework | platform | tool
    private String description;
    private String version;

    public Technology(String name, String category, String description, String version) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.version = version;
    }
}
