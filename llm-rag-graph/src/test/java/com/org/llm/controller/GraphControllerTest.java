package com.org.llm.controller;

import com.org.llm.config.SecurityConfig;
import com.org.llm.config.SecurityProperties;
import com.org.llm.domain.*;
import com.org.llm.dto.GraphStats;
import com.org.llm.repository.*;
import com.org.llm.service.GraphRAGService;
import com.org.llm.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {GraphController.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.security.auth-enabled=false")
@Import({SecurityConfig.class, SecurityProperties.class})
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
    @MockitoBean
    private Neo4jClient neo4jClient;

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
        when(ragService.getStats()).thenReturn(new GraphStats(1L, 3L, 6L, 10L, 4L, 8L, 32L, 42L));

        mockMvc.perform(get("/api/v1/graph/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees").value(10));
    }

    @Test
    @DisplayName("Hierarchy endpoint returns the company when found")
    void hierarchyReturnsCompanyWhenFound() throws Exception {
        when(companyRepo.findWithFullHierarchy("TechCorp")).thenReturn(Optional.of(techCorp));

        mockMvc.perform(get("/api/v1/graph/companies/TechCorp/hierarchy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("TechCorp"));
    }

    @Test
    @DisplayName("Hierarchy endpoint returns 404 when the company is not found")
    void hierarchyReturns404WhenMissing() throws Exception {
        when(companyRepo.findWithFullHierarchy("Unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/graph/companies/Unknown/hierarchy"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Employees endpoint returns the list of employees for a company")
    void employeesReturnsPagedList() throws Exception {
        when(employeeRepo.findByCompanyName("TechCorp", 0, 20)).thenReturn(List.of(alice, manager));

        mockMvc.perform(get("/api/v1/graph/companies/TechCorp/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("Employee context endpoint returns the employee when found")
    void employeeContextReturnsEmployeeWhenFound() throws Exception {
        when(employeeRepo.findWithFullContext("Alice Chen")).thenReturn(Optional.of(alice));

        mockMvc.perform(get("/api/v1/graph/employees/Alice Chen/context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Chen"));
    }

    @Test
    @DisplayName("Employee context endpoint returns 404 when the employee is not found")
    void employeeContextReturns404WhenMissing() throws Exception {
        when(employeeRepo.findWithFullContext(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/graph/employees/Unknown/context"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Direct reports endpoint returns the list of direct reports for a manager")
    void directReportsReturnsList() throws Exception {
        when(employeeRepo.findDirectReports("Boss Person", 0, 20)).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/v1/graph/employees/Boss Person/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Project team endpoint returns the list of employees assigned to a project")
    void projectTeamReturnsList() throws Exception {
        when(employeeRepo.findByProjectName("Project Alpha", 0, 20)).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/v1/graph/projects/Project Alpha/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Export endpoint maps the node and relationship queries into D3 nodes and links")
    void exportBuildsNodesAndLinksAcrossEntireGraph() throws Exception {
        Neo4jClient.UnboundRunnableSpec nodes = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        Neo4jClient.UnboundRunnableSpec links = mock(Neo4jClient.UnboundRunnableSpec.class, RETURNS_DEEP_STUBS);
        when(neo4jClient.query(startsWith("MATCH (n) WHERE"))).thenReturn(nodes);
        when(neo4jClient.query(startsWith("MATCH (n)-[r]->(m)"))).thenReturn(links);
        when(nodes.fetch().all()).thenReturn(List.of(
                Map.of("id", 1L, "label", "Company", "name", "TechCorp"),
                Map.of("id", 2L, "label", "Department", "name", "Engineering"),
                Map.of("id", 3, "label", "Employee", "name", "Alice Chen"),
                Map.of("id", 4L, "label", "Employee")));                       // no name -> dropped
        when(links.fetch().all()).thenReturn(List.of(
                Map.of("source", 1L, "target", 2L, "relType", "HAS_DEPARTMENT"),
                Map.of("source", 2L, "target", 3, "relType", "EMPLOYS")));

        mockMvc.perform(get("/api/v1/graph/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes.length()").value(3))
                .andExpect(jsonPath("$.nodes[2].id").value(3))
                .andExpect(jsonPath("$.links.length()").value(2))
                .andExpect(jsonPath("$.links[0].type").value("HAS_DEPARTMENT"));
    }

    @Test
    @DisplayName("Negative limit/offset are clamped instead of reaching Cypher as SKIP -1 / LIMIT -5")
    void negativePaginationIsClamped() throws Exception {
        when(employeeRepo.findByCompanyName("TechCorp", 0, 1)).thenReturn(List.of(alice));

        mockMvc.perform(get("/api/v1/graph/companies/TechCorp/employees").param("limit", "-5").param("offset", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
