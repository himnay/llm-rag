package com.org;

import com.org.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class DatabaseConnectivityTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testDatabaseConnectivity() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        System.out.println("DB connectivity check result = " + result);
    }
}
