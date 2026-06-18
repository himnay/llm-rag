package com.org.llm.config;

import com.org.llm.controller.GraphController;
import com.org.llm.dto.GraphStats;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the {@code authEnabled = false} branch of {@link SecurityConfig#filterChain}: every
 * request is permitted through without authentication.
 */
@WebMvcTest(controllers = GraphController.class)
@Import({SecurityConfig.class, SecurityProperties.class})
@TestPropertySource(properties = "app.security.auth-enabled=false")
class SecurityConfigAuthDisabledTest {

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

    @DisplayName("Unauthenticated request to /api/** is permitted when auth is disabled")
    @Test
    void unauthenticatedRequestToApiIsPermitted() throws Exception {
        when(ragService.getStats()).thenReturn(new GraphStats(1, 1, 1, 1, 1, 1, 6, 6));

        mockMvc.perform(get("/api/graph/stats"))
                .andExpect(status().isOk());
    }
}
