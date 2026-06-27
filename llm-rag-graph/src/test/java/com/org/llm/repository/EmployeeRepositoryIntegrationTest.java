package com.org.llm.repository;

import com.org.llm.TestcontainersConfiguration;
import com.org.llm.domain.Employee;
import com.org.llm.service.AnthropicLLMService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class EmployeeRepositoryIntegrationTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private AnthropicLLMService anthropicLLMService;

    @BeforeEach
    void cleanup() {
        employeeRepository.deleteAll();
    }

    private Employee newEmployee(String name, String title, String email) {
        return new Employee(name, title, email,
                name + " is a " + title + ".",
                List.of("Java", "Spring"), 5);
    }

    @Test
    @DisplayName("Saving an employee persists it as a node and returns it with an assigned id")
    void save_persistsEmployeeNode() {
        Employee employee = newEmployee("Alice Chen", "Principal Engineer", "alice@techcorp.com");

        Employee saved = employeeRepository.save(employee);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Alice Chen");
        assertThat(saved.getEmail()).isEqualTo("alice@techcorp.com");
    }

    @Test
    @DisplayName("findByName returns the employee matching the given name")
    void findByName_returnsMatchingEmployee() {
        employeeRepository.save(newEmployee("Bob Lee", "Backend Engineer", "bob@techcorp.com"));

        Optional<Employee> found = employeeRepository.findByName("Bob Lee");

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Backend Engineer");
    }

    @Test
    @DisplayName("findByName returns empty when no employee matches the name")
    void findByName_returnsEmpty_whenNotFound() {
        Optional<Employee> found = employeeRepository.findByName("No One");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findByEmail returns the employee matching the given email")
    void findByEmail_returnsMatchingEmployee() {
        employeeRepository.save(newEmployee("Carol Wu", "Staff Engineer", "carol@techcorp.com"));

        Optional<Employee> found = employeeRepository.findByEmail("carol@techcorp.com");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Carol Wu");
    }

    @Test
    @DisplayName("searchByKeyword matches employees by name or title")
    void searchByKeyword_matchesNameAndTitle() {
        employeeRepository.save(newEmployee("Dave Kim", "DevOps Engineer", "dave@techcorp.com"));
        employeeRepository.save(newEmployee("Eve Ng", "Frontend Engineer", "eve@techcorp.com"));

        List<Employee> results = employeeRepository.searchByKeyword("devops");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Dave Kim");
    }

    @Test
    @DisplayName("count reflects the number of persisted employee nodes")
    void count_reflectsPersistedNodes() {
        employeeRepository.save(newEmployee("Frank Doe", "QA Engineer", "frank@techcorp.com"));
        employeeRepository.save(newEmployee("Grace Hall", "EM", "grace@techcorp.com"));

        assertThat(employeeRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("deleteAll removes every employee node from the graph")
    void deleteAll_removesAllNodes() {
        employeeRepository.save(newEmployee("Henry Fox", "CTO", "henry@techcorp.com"));

        employeeRepository.deleteAll();

        assertThat(employeeRepository.count()).isZero();
    }
}
