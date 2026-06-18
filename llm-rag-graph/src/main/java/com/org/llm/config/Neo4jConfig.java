package com.org.llm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Neo4jConfig {

    /**
     * Full-text index DDL — created once at startup.
     * Covers all searchable text properties across every node label.
     */
    public static final String FULLTEXT_INDEX_QUERY = """
            CREATE FULLTEXT INDEX entitySearch IF NOT EXISTS
            FOR (n:Company|Department|Team|Employee|Project|Technology)
            ON EACH [n.name, n.description, n.bio, n.focus, n.goal, n.purpose]
            """;

    private final Neo4jClient neo4jClient;

    /**
     * Registers the Neo4j-backed transaction manager used for {@code @Transactional} methods.
     */
    @Bean
    public PlatformTransactionManager transactionManager(Driver driver,
                                                         DatabaseSelectionProvider selectionProvider) {
        return new Neo4jTransactionManager(driver, selectionProvider);
    }

    /**
     * Creates standard range indexes and the full-text entity search index at startup.
     * Uses IF NOT EXISTS so repeated restarts are safe (idempotent).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexes() {
        List<String> indexStatements = List.of(
                "CREATE INDEX employee_name IF NOT EXISTS FOR (e:Employee) ON (e.name)",
                "CREATE INDEX project_name IF NOT EXISTS FOR (p:Project) ON (p.name)",
                "CREATE INDEX department_name IF NOT EXISTS FOR (d:Department) ON (d.name)",
                "CREATE INDEX company_name IF NOT EXISTS FOR (c:Company) ON (c.name)",
                FULLTEXT_INDEX_QUERY
        );
        for (String stmt : indexStatements) {
            try {
                neo4jClient.query(stmt).run();
                log.debug("Index DDL executed: {}", stmt.lines().findFirst().orElse(stmt));
            } catch (Exception e) {
                log.warn("Index DDL skipped ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
