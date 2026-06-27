package com.org.llm.repository;

import com.org.llm.domain.Project;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends Neo4jRepository<Project, Long> {

    /**
     * Finds a project by exact name match.
     */
    Optional<Project> findByName(String name);

    /**
     * Finds projects whose name, description, or goal contains the keyword (case-insensitive).
     */
    @Query("""
            MATCH (p:Project)
            WHERE toLower(p.name) CONTAINS toLower($keyword)
               OR toLower(p.description) CONTAINS toLower($keyword)
               OR toLower(p.goal) CONTAINS toLower($keyword)
            RETURN p
            """)
    List<Project> searchByKeyword(String keyword);

    @Query("""
            UNWIND $keywords AS kw
            MATCH (p:Project)
            WHERE toLower(p.name) CONTAINS toLower(kw)
               OR toLower(p.description) CONTAINS toLower(kw)
               OR toLower(p.goal) CONTAINS toLower(kw)
            RETURN DISTINCT p
            """)
    List<Project> searchByKeywords(List<String> keywords);

    /**
     * Finds the name of the department that owns the named project.
     */
    @Query("""
            MATCH (d:Department)-[:OWNS_PROJECT]->(p:Project {name: $name})
            RETURN d.name AS departmentName
            """)
    String findOwningDepartment(String name);

    /**
     * Finds projects that use the named technology.
     */
    @Query("""
            MATCH (p:Project)-[:USES_TECHNOLOGY]->(t:Technology {name: $techName})
            RETURN p
            """)
    List<Project> findByTechnology(String techName);
}
