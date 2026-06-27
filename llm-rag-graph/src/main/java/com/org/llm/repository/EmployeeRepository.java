package com.org.llm.repository;

import com.org.llm.domain.Employee;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends Neo4jRepository<Employee, Long> {

    /**
     * Finds an employee by exact name match.
     */
    Optional<Employee> findByName(String name);

    /**
     * Finds an employee by exact email match.
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Full context: employee + manager + team + department + projects.
     */
    @Query("""
            MATCH (e:Employee {name: $name})
            OPTIONAL MATCH (e)-[:REPORTS_TO]->(mgr:Employee)
            OPTIONAL MATCH (t:Team)-[:HAS_MEMBER]->(e)
            OPTIONAL MATCH (d:Department)-[:HAS_TEAM]->(t)
            OPTIONAL MATCH (e)-[:WORKS_ON]->(p:Project)
            OPTIONAL MATCH (p)-[:USES_TECHNOLOGY]->(tech:Technology)
            RETURN e, mgr, t, d, collect(p), collect(tech)
            """)
    Optional<Employee> findWithFullContext(String name);

    /**
     * Finds employees whose name, title, bio, or skills contain the keyword (case-insensitive).
     */
    @Query("""
            MATCH (e:Employee)
            WHERE toLower(e.name) CONTAINS toLower($keyword)
               OR toLower(e.title) CONTAINS toLower($keyword)
               OR toLower(e.bio) CONTAINS toLower($keyword)
               OR ANY(skill IN e.skills WHERE toLower(skill) CONTAINS toLower($keyword))
            RETURN e
            """)
    List<Employee> searchByKeyword(String keyword);

    /**
     * Employees working on a given project, with Cypher-level pagination.
     */
    @Query("""
            MATCH (e:Employee)-[:WORKS_ON]->(p:Project {name: $projectName})
            RETURN e
            SKIP $offset LIMIT $limit
            """)
    List<Employee> findByProjectName(String projectName, int offset, int limit);

    /**
     * Direct reports of a manager, with Cypher-level pagination.
     */
    @Query("MATCH (e:Employee)-[:REPORTS_TO]->(mgr:Employee {name: $managerName}) RETURN e SKIP $offset LIMIT $limit")
    List<Employee> findDirectReports(String managerName, int offset, int limit);

    /**
     * All employees belonging to a company (traverses 3 hops), with Cypher-level pagination.
     */
    @Query("""
            MATCH (c:Company {name: $companyName})-[:HAS_DEPARTMENT]->(:Department)-[:HAS_TEAM]->(:Team)-[:HAS_MEMBER]->(e:Employee)
            RETURN e
            SKIP $offset LIMIT $limit
            """)
    List<Employee> findByCompanyName(String companyName, int offset, int limit);
}
