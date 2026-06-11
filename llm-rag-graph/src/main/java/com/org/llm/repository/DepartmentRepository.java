package com.org.llm.repository;

import com.org.llm.domain.Department;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends Neo4jRepository<Department, Long> {

    Optional<Department> findByName(String name);

    @Query("MATCH (c:Company)-[:HAS_DEPARTMENT]->(d:Department) WHERE d.name = $name RETURN d")
    Optional<Department> findByNameWithProjects(String name);

    @Query("""
            MATCH (d:Department)
            WHERE toLower(d.name) CONTAINS toLower($keyword)
               OR toLower(d.description) CONTAINS toLower($keyword)
               OR toLower(d.focus) CONTAINS toLower($keyword)
            RETURN d
            """)
    List<Department> searchByKeyword(String keyword);

    @Query("MATCH (d1:Department)-[:COLLABORATES_WITH]->(d2:Department) WHERE d1.name = $name RETURN d2")
    List<Department> findCollaborators(String name);
}
