package com.org.llm.repository;

import com.org.llm.domain.Employee;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends Neo4jRepository<Employee, Long> {

    Optional<Employee> findByName(String name);

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
     * Employees working on a given project.
     */
    @Query("""
            MATCH (e:Employee)-[:WORKS_ON]->(p:Project {name: $projectName})
            RETURN e
            """)
    List<Employee> findByProjectName(String projectName);

    /**
     * Direct reports of a manager.
     */
    @Query("MATCH (e:Employee)-[:REPORTS_TO]->(mgr:Employee {name: $managerName}) RETURN e")
    List<Employee> findDirectReports(String managerName);

    /**
     * All employees belonging to a company (traverses 3 hops).
     */
    @Query("""
            MATCH (c:Company {name: $companyName})-[:HAS_DEPARTMENT]->(:Department)-[:HAS_TEAM]->(:Team)-[:HAS_MEMBER]->(e:Employee)
            RETURN e
            """)
    List<Employee> findByCompanyName(String companyName);
}
