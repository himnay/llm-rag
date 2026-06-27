package com.org.llm.repository;

import com.org.llm.domain.Technology;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TechnologyRepository extends Neo4jRepository<Technology, Long> {

    /**
     * Finds a technology by exact name match.
     */
    Optional<Technology> findByName(String name);

    /**
     * Finds technologies whose name, category, or description contains the keyword (case-insensitive).
     */
    @Query("""
            MATCH (t:Technology)
            WHERE toLower(t.name) CONTAINS toLower($keyword)
               OR toLower(t.category) CONTAINS toLower($keyword)
               OR toLower(t.description) CONTAINS toLower($keyword)
            RETURN t
            """)
    List<Technology> searchByKeyword(String keyword);

    @Query("""
            UNWIND $keywords AS kw
            MATCH (t:Technology)
            WHERE toLower(t.name) CONTAINS toLower(kw)
               OR toLower(t.category) CONTAINS toLower(kw)
               OR toLower(t.description) CONTAINS toLower(kw)
            RETURN DISTINCT t
            """)
    List<Technology> searchByKeywords(List<String> keywords);

    /**
     * Finds technologies in the given category.
     */
    List<Technology> findByCategory(String category);
}
