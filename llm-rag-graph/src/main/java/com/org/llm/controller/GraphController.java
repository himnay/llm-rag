package com.org.llm.controller;

import com.org.llm.domain.Company;
import com.org.llm.domain.Employee;
import com.org.llm.dto.GraphStats;
import com.org.llm.repository.CompanyRepository;
import com.org.llm.repository.EmployeeRepository;
import com.org.llm.service.GraphRAGService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
public class GraphController {

    private final GraphRAGService    ragService;
    private final CompanyRepository  companyRepo;
    private final EmployeeRepository employeeRepo;

    @GetMapping("/stats")
    public ResponseEntity<GraphStats> stats() {
        return ResponseEntity.ok(ragService.getStats());
    }

    @GetMapping("/companies/{name}/hierarchy")
    public ResponseEntity<Company> hierarchy(@PathVariable String name) {
        return companyRepo.findWithFullHierarchy(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/companies/{name}/employees")
    public ResponseEntity<List<Employee>> employees(@PathVariable String name) {
        return ResponseEntity.ok(employeeRepo.findByCompanyName(name));
    }

    @GetMapping("/employees/{name}/context")
    public ResponseEntity<Employee> employeeContext(@PathVariable String name) {
        return employeeRepo.findWithFullContext(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/employees/{name}/reports")
    public ResponseEntity<List<Employee>> directReports(@PathVariable String name) {
        return ResponseEntity.ok(employeeRepo.findDirectReports(name));
    }

    @GetMapping("/projects/{name}/team")
    public ResponseEntity<List<Employee>> projectTeam(@PathVariable String name) {
        return ResponseEntity.ok(employeeRepo.findByProjectName(name));
    }
}
