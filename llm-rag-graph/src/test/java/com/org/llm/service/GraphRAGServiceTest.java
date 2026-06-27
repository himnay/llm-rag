package com.org.llm.service;

import com.org.llm.dto.GraphStats;
import com.org.llm.dto.RagRequest;
import com.org.llm.dto.RagResponse;
import com.org.llm.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphRAGServiceTest {

    @Mock
    private GraphContextExtractor graphContextExtractor;
    @Mock
    private AnthropicLLMService llmService;
    @Mock
    private CompanyRepository companyRepo;
    @Mock
    private DepartmentRepository deptRepo;
    @Mock
    private TeamRepository teamRepo;
    @Mock
    private EmployeeRepository employeeRepo;
    @Mock
    private ProjectRepository projectRepo;
    @Mock
    private TechnologyRepository techRepo;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    @InjectMocks
    private GraphRAGService service;

    @Test
    @DisplayName("Query retrieves graph context and answers using the LLM")
    void queryRetrievesGraphContextAndAnswersWithLlm() {
        var ctx = new GraphContextExtractor.GraphContext(
                "Alice Chen is in the Backend Team.", List.of("Alice Chen"), List.of());
        when(graphContextExtractor.extract("Who is Alice?")).thenReturn(ctx);
        when(llmService.answer("Who is Alice?", ctx.formattedContext()))
                .thenReturn("Alice Chen is a Principal Engineer on the Backend Team.");

        RagResponse response = service.query(new RagRequest("Who is Alice?"));

        assertThat(response.getQuestion()).isEqualTo("Who is Alice?");
        assertThat(response.getAnswer()).contains("Principal Engineer");
        assertThat(response.getGraphContext()).isEqualTo(ctx.formattedContext());
        assertThat(response.getRelevantEntities()).containsExactly("Alice Chen");
        assertThat(response.getProcessingTimeMs()).isGreaterThanOrEqualTo(0);
        verify(llmService).answer("Who is Alice?", ctx.formattedContext());
    }

    @Test
    @DisplayName("LLM call failure propagates as LlmCallException")
    void llmCallFailurePropagatesAsLlmCallException() {
        var ctx = new GraphContextExtractor.GraphContext("context", List.of("Alice"), List.of());
        when(graphContextExtractor.extract(anyString())).thenReturn(ctx);
        when(llmService.answer(anyString(), anyString()))
                .thenThrow(new LlmCallException("Connection refused", new RuntimeException()));

        assertThatThrownBy(() -> service.query(new RagRequest("Who is Alice?")))
                .isInstanceOf(LlmCallException.class)
                .hasMessageContaining("Connection refused");
    }

    @Test
    @DisplayName("Stats aggregates node and relationship counts across repositories")
    void statsAggregatesNodeAndRelationshipCounts() {
        when(companyRepo.count()).thenReturn(1L);
        when(deptRepo.count()).thenReturn(3L);
        when(teamRepo.count()).thenReturn(6L);
        when(employeeRepo.count()).thenReturn(10L);
        when(projectRepo.count()).thenReturn(4L);
        when(techRepo.count()).thenReturn(8L);
        when(neo4jClient.query(anyString())
                .fetchAs(Long.class)
                .mappedBy(any())
                .one()).thenReturn(Optional.of(42L));

        GraphStats stats = service.getStats();

        assertThat(stats.getTotalNodes()).isEqualTo(32L);
        assertThat(stats.getTotalRelationships()).isEqualTo(42L);
        assertThat(stats.getEmployees()).isEqualTo(10L);
    }
}
