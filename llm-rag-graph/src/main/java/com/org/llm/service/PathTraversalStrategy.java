package com.org.llm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Cypher path-traversal strategy: runs multi-level path queries (hierarchy, project/technology,
 * management chain, collaboration) to produce richly-connected context sentences.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PathTraversalStrategy implements GraphExtractionStrategy {

    private final Neo4jClient neo4jClient;

    @Override
    public String name() {
        return "path-traversal";
    }

    /**
     * Runs hierarchy, project/technology, management-chain, and collaboration path queries for
     * each keyword and returns the combined natural-language context lines.
     */
    @Override
    public List<String> extract(List<String> keywords) {
        List<String> lines = new ArrayList<>();
        for (String keyword : keywords) {
            lines.addAll(queryHierarchyPaths(keyword));
            lines.addAll(queryProjectPaths(keyword));
            lines.addAll(queryManagementChain(keyword));
            lines.addAll(queryCollaborationPaths(keyword));
        }
        return lines;
    }

    private List<String> queryHierarchyPaths(String keyword) {
        try {
            return neo4jClient.query("""
                            MATCH (c:Company)-[:HAS_DEPARTMENT]->(d:Department)-[:HAS_TEAM]->(t:Team)-[:HAS_MEMBER]->(e:Employee)
                            WHERE toLower(e.name) CONTAINS toLower($kw)
                               OR toLower(d.name) CONTAINS toLower($kw)
                               OR toLower(t.name) CONTAINS toLower($kw)
                               OR any(skill IN e.skills WHERE toLower(skill) CONTAINS toLower($kw))
                            RETURN c.name AS company, d.name AS dept, t.name AS team, e.name AS employee LIMIT 10
                            """)
                    .bind(keyword).to("kw").fetch().all().stream()
                    .map(r -> String.format("%s → %s → %s → %s",
                            r.get("company"), r.get("dept"), r.get("team"), r.get("employee")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Hierarchy path query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> queryProjectPaths(String keyword) {
        try {
            return neo4jClient.query("""
                            MATCH (e:Employee)-[w:WORKS_ON]->(p:Project)-[:USES_TECHNOLOGY]->(t:Technology)
                            WHERE toLower(e.name) CONTAINS toLower($kw)
                               OR toLower(p.name) CONTAINS toLower($kw)
                               OR toLower(t.name) CONTAINS toLower($kw)
                            RETURN e.name AS employee, w.role AS role, p.name AS project,
                                   collect(DISTINCT t.name) AS technologies LIMIT 10
                            """)
                    .bind(keyword).to("kw").fetch().all().stream()
                    .map(r -> String.format("%s works on %s as %s using %s",
                            r.get("employee"), r.get("project"), r.get("role"), r.get("technologies")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Project path query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> queryManagementChain(String keyword) {
        try {
            return neo4jClient.query("""
                            MATCH chain = (e:Employee)-[:REPORTS_TO*1..3]->(mgr:Employee)
                            WHERE toLower(e.name) CONTAINS toLower($kw) OR toLower(mgr.name) CONTAINS toLower($kw)
                            WITH e, mgr, length(chain) AS depth
                            RETURN e.name AS employee, mgr.name AS manager, depth ORDER BY depth LIMIT 10
                            """)
                    .bind(keyword).to("kw").fetch().all().stream()
                    .map(r -> String.format("%s reports to %s (chain depth %s)",
                            r.get("employee"), r.get("manager"), r.get("depth")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Management chain query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> queryCollaborationPaths(String keyword) {
        try {
            return neo4jClient.query("""
                            MATCH (d1:Department)-[:COLLABORATES_WITH]->(d2:Department)
                            WHERE toLower(d1.name) CONTAINS toLower($kw) OR toLower(d2.name) CONTAINS toLower($kw)
                            RETURN d1.name AS dept1, d2.name AS dept2 LIMIT 10
                            """)
                    .bind(keyword).to("kw").fetch().all().stream()
                    .map(r -> String.format("Department %s collaborates with %s", r.get("dept1"), r.get("dept2")))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Collaboration path query failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
