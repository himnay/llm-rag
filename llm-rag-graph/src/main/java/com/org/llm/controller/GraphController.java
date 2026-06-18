package com.org.llm.controller;

import com.org.llm.domain.Company;
import com.org.llm.domain.Employee;
import com.org.llm.dto.GraphExportDto;
import com.org.llm.dto.GraphStats;
import com.org.llm.repository.*;
import com.org.llm.service.GraphRAGService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private static final int MAX_LIMIT = 100;

    private final GraphRAGService ragService;
    private final CompanyRepository companyRepo;
    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository departmentRepo;
    private final ProjectRepository projectRepo;
    private final TechnologyRepository techRepo;

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
     * and {@code offset}.
     */
    @GetMapping("/companies/{name}/employees")
    public ResponseEntity<List<Employee>> employees(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        List<Employee> all = employeeRepo.findByCompanyName(name);
        List<Employee> page = all.stream().skip(offset).limit(cappedLimit).toList();
        return ResponseEntity.ok(page);
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
     * {@value #MAX_LIMIT}) and {@code offset}.
     */
    @GetMapping("/employees/{name}/reports")
    public ResponseEntity<List<Employee>> directReports(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        List<Employee> all = employeeRepo.findDirectReports(name);
        List<Employee> page = all.stream().skip(offset).limit(cappedLimit).toList();
        return ResponseEntity.ok(page);
    }

    /**
     * Lists employees working on a project, paginated via {@code limit} (capped at
     * {@value #MAX_LIMIT}) and {@code offset}.
     */
    @GetMapping("/projects/{name}/team")
    public ResponseEntity<List<Employee>> projectTeam(
            @PathVariable String name,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        int cappedLimit = Math.min(limit, MAX_LIMIT);
        List<Employee> all = employeeRepo.findByProjectName(name);
        List<Employee> page = all.stream().skip(offset).limit(cappedLimit).toList();
        return ResponseEntity.ok(page);
    }

    /**
     * Export the full graph in D3.js-compatible format for visualization.
     * {@code GET /api/graph/export?format=json}
     */
    @GetMapping("/export")
    public ResponseEntity<GraphExportDto> export(
            @RequestParam(defaultValue = "json") String format) {
        List<GraphExportDto.GraphNode> nodes = new ArrayList<>();
        List<GraphExportDto.GraphLink> links = new ArrayList<>();

        // Collect all nodes
        companyRepo.findAll().forEach(c -> {
            if (c.getId() != null) {
                nodes.add(new GraphExportDto.GraphNode(c.getId(), "Company", c.getName()));
                c.getDepartments().forEach(d -> {
                    if (d.getId() != null) {
                        links.add(new GraphExportDto.GraphLink(c.getId(), d.getId(), "HAS_DEPARTMENT"));
                    }
                });
            }
        });

        departmentRepo.findAll().forEach(d -> {
            if (d.getId() != null) {
                nodes.add(new GraphExportDto.GraphNode(d.getId(), "Department", d.getName()));
                d.getProjects().forEach(p -> {
                    if (p.getId() != null) {
                        links.add(new GraphExportDto.GraphLink(d.getId(), p.getId(), "OWNS_PROJECT"));
                    }
                });
                d.getCollaborators().forEach(c -> {
                    if (c.getId() != null) {
                        links.add(new GraphExportDto.GraphLink(d.getId(), c.getId(), "COLLABORATES_WITH"));
                    }
                });
            }
        });

        projectRepo.findAll().forEach(p -> {
            if (p.getId() != null) {
                nodes.add(new GraphExportDto.GraphNode(p.getId(), "Project", p.getName()));
                p.getTechnologies().forEach(t -> {
                    if (t.getId() != null) {
                        links.add(new GraphExportDto.GraphLink(p.getId(), t.getId(), "USES_TECHNOLOGY"));
                    }
                });
            }
        });

        techRepo.findAll().forEach(t -> {
            if (t.getId() != null) {
                nodes.add(new GraphExportDto.GraphNode(t.getId(), "Technology", t.getName()));
            }
        });

        employeeRepo.findAll().forEach(e -> {
            if (e.getId() != null) {
                nodes.add(new GraphExportDto.GraphNode(e.getId(), "Employee", e.getName()));
                if (e.getManager() != null && e.getManager().getId() != null) {
                    links.add(new GraphExportDto.GraphLink(e.getId(), e.getManager().getId(), "REPORTS_TO"));
                }
                if (e.getProjectAssignments() != null) {
                    e.getProjectAssignments().forEach(wa -> {
                        if (wa.getProject() != null && wa.getProject().getId() != null) {
                            links.add(new GraphExportDto.GraphLink(e.getId(), wa.getProject().getId(), "WORKS_ON"));
                        }
                    });
                }
            }
        });

        return ResponseEntity.ok(new GraphExportDto(nodes, links));
    }
}
