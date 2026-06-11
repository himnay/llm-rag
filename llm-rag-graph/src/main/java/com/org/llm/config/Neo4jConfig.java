package com.org.llm.config;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
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

    @Bean
    public PlatformTransactionManager transactionManager(Driver driver,
                                                         DatabaseSelectionProvider selectionProvider) {
        return new Neo4jTransactionManager(driver, selectionProvider);
    }
}
