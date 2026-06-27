package com.org.llm.repository;

import com.org.llm.domain.Department;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends Neo4jRepository<Department, Long> {

    /**
     * Finds a department by exact name match.
     */
    Optional<Department> findByName(String name);

    /**
     * Finds a department by name, with its owned projects loaded.
     */
    @Query("MATCH (c:Company)-[:HAS_DEPARTMENT]->(d:Department) WHERE d.name = $name RETURN d")
    Optional<Department> findByNameWithProjects(String name);

    /**
     * Finds departments whose name, description, or focus contains the keyword (case-insensitive).
     */
    @Query("""
            MATCH (d:Department)
            WHERE toLower(d.name) CONTAINS toLower($keyword)
               OR toLower(d.description) CONTAINS toLower($keyword)
               OR toLower(d.focus) CONTAINS toLower($keyword)
            RETURN d
            """)
    List<Department> searchByKeyword(String keyword);

    @Query("""
            UNWIND $keywords AS kw
            MATCH (d:Department)
            WHERE toLower(d.name) CONTAINS toLower(kw)
               OR toLower(d.description) CONTAINS toLower(kw)
               OR toLower(d.focus) CONTAINS toLower(kw)
            RETURN DISTINCT d
            """)
    List<Department> searchByKeywords(List<String> keywords);

    /**
     * Finds departments that the named department collaborates with.
     */
    @Query("MATCH (d1:Department)-[:COLLABORATES_WITH]->(d2:Department) WHERE d1.name = $name RETURN d2")
    List<Department> findCollaborators(String name);
}
