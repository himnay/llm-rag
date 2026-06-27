package com.org.llm.service;

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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphDataSeederTest {

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
    private GraphDataSeeder seeder;

    @Test
    @DisplayName("Seeding is skipped when the graph is already populated")
    void skipsSeedingWhenGraphAlreadyPopulated() {
        when(companyRepo.count()).thenReturn(1L);

        seeder.seed();

        verify(projectRepo, never()).save(any());
        verify(techRepo, never()).save(any());
    }

    @Test
    @DisplayName("Seeds the full graph with companies, technologies and projects when empty")
    void seedsFullGraphWhenEmpty() {
        when(companyRepo.count()).thenReturn(0L);
        when(techRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(neo4jClient.query(org.mockito.ArgumentMatchers.anyString()).run())
                .thenReturn(null);

        assertThatCode(seeder::seed).doesNotThrowAnyException();

        verify(companyRepo).save(any());
        verify(techRepo, org.mockito.Mockito.atLeast(10)).save(any());
        verify(projectRepo, org.mockito.Mockito.atLeast(5)).save(any());
    }

    @Test
    @DisplayName("Seeding tolerates full-text index creation failure and still saves data")
    void seedingToleratesFullTextIndexCreationFailure() {
        when(companyRepo.count()).thenReturn(0L);
        when(techRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(projectRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(neo4jClient.query(org.mockito.ArgumentMatchers.anyString()).run())
                .thenThrow(new RuntimeException("index already exists"));

        assertThatCode(seeder::seed).doesNotThrowAnyException();

        verify(companyRepo).save(any());
    }
}
