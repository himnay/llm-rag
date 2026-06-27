package com.org.llm.controller;

import com.org.llm.domain.*;
import com.org.llm.dto.GraphStats;
import com.org.llm.repository.*;
import com.org.llm.service.GraphRAGService;
import com.org.llm.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {GraphController.class, GlobalExceptionHandler.class},
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
class GraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GraphRAGService ragService;
    @MockitoBean
    private CompanyRepository companyRepo;
    @MockitoBean
    private EmployeeRepository employeeRepo;
    @MockitoBean
    private DepartmentRepository departmentRepo;
    @MockitoBean
    private ProjectRepository projectRepo;
    @MockitoBean
    private TechnologyRepository techRepo;

    private Technology java;
    private Project alpha;
    private Department engineering;
    private Department product;
    private Company techCorp;
    private Employee manager;
    private Employee alice;

    @BeforeEach
    void setUp() {
        java = new Technology("Java", "language", "JVM language", "21");
        java.setId(5L);

        alpha = new Project("Project Alpha", "desc", "active", "2023-01-01", "goal");
        alpha.setId(4L);
        alpha.setTechnologies(List.of(java));

        product = new Department("Product", "focus", "desc", 10);
        product.setId(3L);

        engineering = new Department("Engineering", "focus", "desc", 100);
        engineering.setId(2L);
        engineering.setProjects(List.of(alpha));
        engineering.setCollaborators(List.of(product));

        techCorp = new Company("TechCorp", "Software", "desc", "2018", "SF");
        techCorp.setId(1L);
        techCorp.setDepartments(List.of(engineering));

        manager = new Employee("Boss Person", "VP", "boss@techcorp.com", "bio", List.of(), 10);
        manager.setId(6L);

        alice = new Employee("Alice Chen", "Engineer", "alice@techcorp.com", "bio", List.of("Java"), 5);
        alice.setId(7L);
        alice.setManager(manager);
        alice.setProjectAssignments(List.of(new WorksOnRelationship("lead", "2023-01-01", 50, alpha)));
    }

    @Test
    @DisplayName("Stats endpoint returns aggregated node and relationship counts")
    void statsReturnsAggregatedCounts() throws Exception {
        when(ragService.getStats()).thenReturn(new GraphStats(1, 3, 6, 10, 4, 8, 32, 42));

        mockMvc.perform(get("/api/graph/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees").value(10));
    }

    @Test
    @DisplayName("Hierarchy endpoint returns the company when found")
    void hierarchyReturnsCompanyWhenFound() throws Exception {
        when(companyRepo.findWithFullHierarchy("TechCorp")).thenReturn(Optional.of(techCorp));

        mockMvc.perform(get("/api/graph/companies/TechCorp/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TechCorp"));
    }

    @Test
    @DisplayName("Hierarchy endpoint returns 404 when the company is not found")
    void hierarchyReturns404WhenMissing() throws Exception {
        when(companyRepo.findWithFullHierarchy("Unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/graph/companies/Unknown/hierarchy"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Employees endpoint returns the list of employees for a company")
    void employeesReturnsPagedList() throws Exception {
        when(employeeRepo.findByCompanyName("TechCorp")).thenReturn(List.of(alice, manager));

        mockMvc.perform(get("/api/graph/companies/TechCorp/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Employee context endpoint returns the employee when found")
    void employeeContextReturnsEmployeeWhenFound() throws Exception {
        when(employeeRepo.findWithFullContext("Alice Chen")).thenReturn(Optional.of(alice));

        mockMvc.perform(get("/api/graph/employees/Alice Chen/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Chen"));
    }

    @Test
    @DisplayName("Employee context endpoint returns 404 when the employee is not found")
    void employeeContextReturns404WhenMissing() throws Exception {
        when(employeeRepo.findWithFullContext(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/graph/employees/Unknown/context"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Direct reports endpoint returns the list of direct reports for a manager")
    void directReportsReturnsList() throws Exception {
        when(employeeRepo.findDirectReports("Boss Person")).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/graph/employees/Boss Person/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Project team endpoint returns the list of employees assigned to a project")
    void projectTeamReturnsList() throws Exception {
        when(employeeRepo.findByProjectName("Project Alpha")).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/graph/projects/Project Alpha/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Export endpoint builds nodes and links spanning the entire graph")
    void exportBuildsNodesAndLinksAcrossEntireGraph() throws Exception {
        when(companyRepo.findAll()).thenReturn(List.of(techCorp));
        when(departmentRepo.findAll()).thenReturn(List.of(engineering, product));
        when(projectRepo.findAll()).thenReturn(List.of(alpha));
        when(techRepo.findAll()).thenReturn(List.of(java));
        when(employeeRepo.findAll()).thenReturn(List.of(alice, manager));

        mockMvc.perform(get("/api/graph/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(7))
                .andExpect(jsonPath("$.links").isArray());
    }
}
