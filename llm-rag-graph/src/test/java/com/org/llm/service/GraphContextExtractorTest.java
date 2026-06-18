package com.org.llm.service;

import com.org.llm.domain.Employee;
import com.org.llm.domain.Project;
import com.org.llm.domain.WorksOnRelationship;
import com.org.llm.repository.DepartmentRepository;
import com.org.llm.repository.EmployeeRepository;
import com.org.llm.repository.ProjectRepository;
import com.org.llm.repository.TechnologyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphContextExtractorTest {

    @Mock
    private Neo4jClient neo4jClient;
    @Mock
    private EmployeeRepository employeeRepo;
    @Mock
    private DepartmentRepository deptRepo;
    @Mock
    private ProjectRepository projectRepo;
    @Mock
    private TechnologyRepository techRepo;

    private GraphContextExtractor extractor;

    private static void injectField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field " + fieldName, e);
        }
    }

    @BeforeEach
    void setUp() {
        extractor = new GraphContextExtractor(neo4jClient, employeeRepo, deptRepo,
                projectRepo, techRepo, List.of());
        // inject @Value fields via reflection (defaults)
        injectField(extractor, "maxContextNodes", 20);
        injectField(extractor, "contextDepth", 4);

        // Full-text search: return empty by default to keep tests simple
        Neo4jClient.UnboundRunnableSpec spec = org.mockito.Mockito.mock(Neo4jClient.UnboundRunnableSpec.class,
                org.mockito.Answers.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(anyString())).thenReturn(spec);
        when(spec.bind(anyString()).to(anyString()).fetch().all().stream()
                .collect(java.util.stream.Collectors.toList()))
                .thenReturn(List.of());
    }

    @DisplayName("Extract returns non-empty context including entity name for a known employee")
    @Test
    void extractReturnsNonEmptyContextForKnownEmployee() {
        Employee emp = new Employee("Alice Chen", "Engineer", "alice@acme.com", "Backend dev",
                List.of("Java", "Spring"), 5);
        emp.getProjectAssignments().add(new WorksOnRelationship("lead", "2024-01", 80, new Project("Alpha", "", "active", "2024-01", "")));

        when(employeeRepo.searchByKeyword(anyString())).thenReturn(List.of(emp));
        when(deptRepo.searchByKeyword(anyString())).thenReturn(List.of());
        when(projectRepo.searchByKeyword(anyString())).thenReturn(List.of());
        when(techRepo.searchByKeyword(anyString())).thenReturn(List.of());

        GraphContextExtractor.GraphContext ctx = extractor.extract("Who is Alice Chen?");

        assertThat(ctx.entityNames()).contains("Alice Chen");
        assertThat(ctx.formattedContext()).contains("Alice Chen");
    }

    @DisplayName("Extract returns no context message when keywords match nothing in the graph")
    @Test
    void extractReturnsNoContextForUnknownKeywords() {
        when(employeeRepo.searchByKeyword(anyString())).thenReturn(List.of());
        when(deptRepo.searchByKeyword(anyString())).thenReturn(List.of());
        when(projectRepo.searchByKeyword(anyString())).thenReturn(List.of());
        when(techRepo.searchByKeyword(anyString())).thenReturn(List.of());

        GraphContextExtractor.GraphContext ctx = extractor.extract("What is the weather today?");

        assertThat(ctx.entityNames()).isEmpty();
        assertThat(ctx.formattedContext()).contains("No relevant graph context found");
    }

    @DisplayName("Extract does not infinite loop when the management chain is cyclic")
    @Test
    void extractDoesNotInfiniteLoopOnCyclicManagementChain() {
        // Create a cycle: emp1 -> emp2 -> emp1 (manager cycle)
        Employee emp1 = new Employee("Bob", "Manager", "bob@acme.com", "", List.of(), 10);
        Employee emp2 = new Employee("Carol", "Lead", "carol@acme.com", "", List.of(), 5);
        emp1.setId(1L);
        emp2.setId(2L);
        emp1.setManager(emp2);
        emp2.setManager(emp1); // cycle

        when(employeeRepo.searchByKeyword(anyString())).thenReturn(List.of(emp1, emp2));
        when(deptRepo.searchByKeyword(anyString())).thenReturn(List.of());
        when(projectRepo.searchByKeyword(anyString())).thenReturn(List.of());
        when(techRepo.searchByKeyword(anyString())).thenReturn(List.of());

        // Should complete without StackOverflow or infinite loop
        assertThatCode(() -> extractor.extract("Who manages Bob?"))
                .doesNotThrowAnyException();
    }
}
