package com.org.llm.repository;

import com.org.llm.domain.Team;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends Neo4jRepository<Team, Long> {

    Optional<Team> findByName(String name);

    @Query("""
            MATCH (d:Department)-[:HAS_TEAM]->(t:Team {name: $teamName})
            RETURN d.name AS departmentName
            """)
    String findDepartmentNameForTeam(String teamName);

    @Query("""
            MATCH (t:Team)
            WHERE toLower(t.name) CONTAINS toLower($keyword)
               OR toLower(t.purpose) CONTAINS toLower($keyword)
            RETURN t
            """)
    List<Team> searchByKeyword(String keyword);
}
