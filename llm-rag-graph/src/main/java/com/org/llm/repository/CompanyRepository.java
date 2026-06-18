package com.org.llm.repository;

import com.org.llm.domain.Company;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends Neo4jRepository<Company, Long> {

    /**
     * Finds a company by exact name match.
     */
    Optional<Company> findByName(String name);

    /**
     * Full 4-level hierarchy: Company → Department → Team → Employee
     */
    @Query("""
            MATCH (c:Company {name: $name})
            OPTIONAL MATCH (c)-[:HAS_DEPARTMENT]->(d:Department)
            OPTIONAL MATCH (d)-[:HAS_TEAM]->(t:Team)
            OPTIONAL MATCH (t)-[:HAS_MEMBER]->(e:Employee)
            RETURN c, collect(d), collect(t), collect(e)
            """)
    Optional<Company> findWithFullHierarchy(String name);

    /**
     * Finds companies whose name or description contains the keyword (case-insensitive).
     */
    @Query("MATCH (c:Company) WHERE toLower(c.name) CONTAINS toLower($keyword) OR toLower(c.description) CONTAINS toLower($keyword) RETURN c")
    List<Company> searchByKeyword(String keyword);
}
