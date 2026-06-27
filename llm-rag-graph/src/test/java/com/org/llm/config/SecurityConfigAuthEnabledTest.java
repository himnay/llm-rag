package com.org.llm.config;

import com.org.llm.controller.GraphController;
import com.org.llm.repository.*;
import com.org.llm.service.GraphRAGService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the {@code authEnabled = true} branch of {@link SecurityConfig#filterChain}: requests
 * to {@code /api/**} must be rejected when unauthenticated.
 */
@Import({SecurityConfig.class, SecurityProperties.class})
@WebMvcTest(controllers = GraphController.class)
@TestPropertySource(properties = "app.security.auth-enabled=true")
class SecurityConfigAuthEnabledTest {

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

    @Test
    @DisplayName("Unauthenticated request to /api/** is rejected when auth is enabled")
    void unauthenticatedRequestToApiIsRejected() throws Exception {
        mockMvc.perform(get("/api/graph/stats"))
                .andExpect(status().is4xxClientError());
    }
}
