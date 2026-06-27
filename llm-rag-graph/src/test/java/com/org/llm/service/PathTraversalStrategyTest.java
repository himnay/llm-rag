package com.org.llm.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PathTraversalStrategyTest {

    @Mock
    private Neo4jClient neo4jClient;

    private PathTraversalStrategy strategy;

    @Test
    @DisplayName("Strategy name is path-traversal")
    void nameIsPathTraversal() {
        strategy = new PathTraversalStrategy(neo4jClient);
        assertThat(strategy.name()).isEqualTo("path-traversal");
    }

    @Test
    @DisplayName("Extract maps all four query kinds to descriptive sentences")
    void extractMapsAllFourQueryKindsToSentences() {
        strategy = new PathTraversalStrategy(neo4jClient);

        stubQuery("HAS_MEMBER", List.of(
                Map.of("company", "TechCorp", "dept", "Engineering", "team", "Backend Team", "employee", "Alice Chen")));
        stubQuery("USES_TECHNOLOGY", List.of(
                Map.of("employee", "Alice Chen", "role", "lead", "project", "Project Alpha",
                        "technologies", List.of("Java", "Spring Boot"))));
        stubQuery("REPORTS_TO*1..3", List.of(
                Map.of("employee", "Bob", "manager", "Alice Chen", "depth", 1L)));
        stubQuery("COLLABORATES_WITH", List.of(
                Map.of("dept1", "Engineering", "dept2", "Product")));

        List<String> lines = strategy.extract(List.of("alice"));

        assertThat(lines).hasSize(4);
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("TechCorp", "Engineering", "Backend Team", "Alice Chen"));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("Alice Chen works on Project Alpha as lead"));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("Bob reports to Alice Chen (chain depth 1)"));
        assertThat(lines).anySatisfy(l -> assertThat(l).contains("Engineering collaborates with Product"));
    }

    @Test
    @DisplayName("Extract returns an empty list when every underlying query fails")
    void extractReturnsEmptyWhenEveryQueryFails() {
        strategy = new PathTraversalStrategy(neo4jClient);
        when(neo4jClient.query(ArgumentMatchers.<String>any())).thenThrow(new RuntimeException("boom"));

        List<String> lines = strategy.extract(List.of("alice"));

        assertThat(lines).isEmpty();
    }

    private void stubQuery(String cypherFragment, List<Map<String, Object>> rows) {
        Neo4jClient.UnboundRunnableSpec spec = mock(Neo4jClient.UnboundRunnableSpec.class, Answers.RETURNS_DEEP_STUBS);
        when(neo4jClient.query(ArgumentMatchers.<String>argThat(s -> s != null && s.contains(cypherFragment)))).thenReturn(spec);
        when(spec.bind(any()).to(anyString()).fetch().all()).thenReturn(rows);
    }
}
