package com.org.llm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Neo4jConfigTest {

    @Mock
    private Driver driver;
    @Mock
    private DatabaseSelectionProvider selectionProvider;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    @DisplayName("transactionManager bean is created as a Neo4jTransactionManager")
    @Test
    void transactionManagerBeanIsCreated() {
        Neo4jConfig config = new Neo4jConfig(neo4jClient);

        PlatformTransactionManager txManager = config.transactionManager(driver, selectionProvider);

        assertThat(txManager).isNotNull().isInstanceOf(Neo4jTransactionManager.class);
    }

    @DisplayName("createIndexes runs all DDL statements without throwing")
    @Test
    void createIndexesRunsAllDdlStatementsWithoutError() {
        Neo4jConfig config = new Neo4jConfig(neo4jClient);

        assertThatCode(config::createIndexes).doesNotThrowAnyException();
    }

    @DisplayName("createIndexes tolerates DDL failures without propagating the exception")
    @Test
    void createIndexesToleratesDdlFailures() {
        Neo4jConfig config = new Neo4jConfig(neo4jClient);
        when(neo4jClient.query(anyString()).run()).thenThrow(new RuntimeException("index DDL failed"));

        assertThatCode(config::createIndexes).doesNotThrowAnyException();
    }
}
