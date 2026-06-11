package com.org.security;

import com.org.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.security.web.FilterChainProxy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that API-key auth actually guards {@code /api/**}: missing key → 401, valid key
 * (provisioned in {@code api_keys}) → through, and actuator stays open. Runs with auth enabled
 * (a dedicated context); shares the Testcontainers from {@link IntegrationTest}.
 */
@TestPropertySource(properties = {"app.security.auth-enabled=true", "app.security.rate-limit.enabled=false"})
class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FilterChainProxy springSecurityFilterChain;
    @Autowired
    JdbcTemplate jdbc;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    void protectedEndpointRejectsMissingKey() throws Exception {
        mvc().perform(post("/api/v1/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAcceptsValidKey() throws Exception {
        String raw = "integration-test-key";
        jdbc.update("INSERT INTO api_keys (key_hash, label) VALUES (?, 'it') ON CONFLICT DO NOTHING",
                ApiKeyService.sha256(raw));

        mvc().perform(post("/api/v1/retrieve")
                        .header("X-API-Key", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"anything\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorHealthStaysOpen() throws Exception {
        mvc().perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
