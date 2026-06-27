package com.org.llm.controller;

import com.org.llm.domain.Company;
import com.org.llm.domain.Employee;
import com.org.llm.dto.GraphExportDto;
import com.org.llm.dto.GraphLink;
import com.org.llm.dto.GraphNode;
import com.org.llm.dto.GraphStats;
import com.org.llm.repository.*;
import com.org.llm.service.GraphRAGService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/graph")
@RequiredArgsConstructor
public class GraphController {

    private static final int MAX_LIMIT = 100;

    private final GraphRAGService ragService;
    private final CompanyRepository companyRepo;
    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository departmentRepo;
    private final ProjectRepository projectRepo;
    private final TechnologyRepository techRepo;
    private final Neo4jClient neo4jClient;

    /**
     * Returns aggregate node/relationship counts for the knowledge graph.
     */
    @GetMapping("/stats")
    public ResponseEntity<GraphStats> stats() {
        return ResponseEntity.ok(ragService.getStats());
    }

    /**
     * Returns the full Company → Department → Team → Employee hierarchy for the named company.
     */
    @GetMapping("/companies/{name}/hierarchy")
    public ResponseEntity<Company> hierarchy(@PathVariable String name) {
        return companyRepo.findWithFullHierarchy(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists employees of a company, paginated via {@code limit} (capped at {@value #MAX_LIMIT})
     * and {@code offset}. Pagination is pushed into Cypher to avoid loading all rows.
     */
    @GetMapping("/companies/{name}/employees")
    public ResponseEntity<List<Employee>> employees(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        return ResponseEntity.ok(employeeRepo.findByCompanyName(name, offset, cappedLimit));
    }

    /**
     * Returns an employee with their manager, team, department, and project assignments loaded.
     */
    @GetMapping("/employees/{name}/context")
    public ResponseEntity<Employee> employeeContext(@PathVariable String name) {
        return employeeRepo.findWithFullContext(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lists the direct reports of a manager, paginated via {@code limit} (capped at
     * {@value #MAX_LIMIT}) and {@code offset}. Pagination is pushed into Cypher.
     */
    @GetMapping("/employees/{name}/reports")
    public ResponseEntity<List<Employee>> directReports(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        return ResponseEntity.ok(employeeRepo.findDirectReports(name, offset, cappedLimit));
    }

    /**
     * Lists employees working on a project, paginated via {@code limit} (capped at
     * {@value #MAX_LIMIT}) and {@code offset}. Pagination is pushed into Cypher.
     */
    @GetMapping("/projects/{name}/team")
    public ResponseEntity<List<Employee>> projectTeam(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        return ResponseEntity.ok(employeeRepo.findByProjectName(name, offset, cappedLimit));
    }

    private static final int EXPORT_NODE_LIMIT = 2000;
    private static final int EXPORT_REL_LIMIT = 10000;
    private static final String NODE_LABEL_FILTER =
            "n:Company OR n:Department OR n:Project OR n:Technology OR n:Employee OR n:Team";

    /**
     * Export the full graph in D3.js-compatible format for visualization.
     * Uses 2 Cypher queries (nodes + relationships) instead of 5 findAll() calls.
     * {@code GET /api/graph/export?format=json}
     */
    @GetMapping("/export")
    public ResponseEntity<GraphExportDto> export(
            @RequestParam(defaultValue = "json") String format) {
        List<GraphNode> nodes = neo4jClient.query(
                        "MATCH (n) WHERE " + NODE_LABEL_FILTER +
                        " RETURN id(n) AS id, labels(n)[0] AS label, n.name AS name" +
                        " LIMIT " + EXPORT_NODE_LIMIT)
                .fetch()
                .all()
                .stream()
                .filter(row -> row.get("id") != null && row.get("name") != null)
                .map(row -> new GraphNode(
                        toLong(row.get("id")),
                        String.valueOf(row.get("label")),
                        String.valueOf(row.get("name"))))
                .toList();

        List<GraphLink> links = neo4jClient.query(
                        "MATCH (n)-[r]->(m)" +
                        " WHERE (" + NODE_LABEL_FILTER.replace("n:", "n:") + ")" +
                        "   AND (m:Company OR m:Department OR m:Project OR m:Technology OR m:Employee OR m:Team)" +
                        " RETURN id(n) AS source, id(m) AS target, type(r) AS relType" +
                        " LIMIT " + EXPORT_REL_LIMIT)
                .fetch()
                .all()
                .stream()
                .filter(row -> row.get("source") != null && row.get("target") != null)
                .map(row -> new GraphLink(
                        toLong(row.get("source")),
                        toLong(row.get("target")),
                        String.valueOf(row.get("relType"))))
                .toList();

        return ResponseEntity.ok(new GraphExportDto(nodes, links));
    }

    private static Long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        return null;
    }
}
