package com.org.llm.repository;

import com.org.llm.domain.Technology;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TechnologyRepository extends Neo4jRepository<Technology, Long> {

    Optional<Technology> findByName(String name);

    @Query("""
            MATCH (t:Technology)
            WHERE toLower(t.name) CONTAINS toLower($keyword)
               OR toLower(t.category) CONTAINS toLower($keyword)
               OR toLower(t.description) CONTAINS toLower($keyword)
            RETURN t
            """)
    List<Technology> searchByKeyword(String keyword);

    List<Technology> findByCategory(String category);
}
