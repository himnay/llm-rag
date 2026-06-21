package com.org.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.org.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end check that Keycloak JWT auth actually guards {@code /api/**}: missing token → 401,
 * valid token (mocked via Spring Security's {@code jwt()} request post-processor, so this test
 * doesn't need a real Keycloak/JWKS) → through, and actuator stays open. Runs with auth enabled
 * (a dedicated context); shares the Testcontainers from {@link IntegrationTest}.
 */
@TestPropertySource(properties = {"app.security.auth-enabled=true", "app.security.rate-limit.enabled=false"})
class SecurityIntegrationTest extends IntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    FilterChainProxy springSecurityFilterChain;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
    }

    @Test
    @DisplayName("Protected endpoint rejects requests with no Bearer token with 401")
    void protectedEndpointRejectsMissingToken() throws Exception {
        mvc().perform(post("/api/v1/retrieve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"anything\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Protected endpoint accepts requests with a valid Bearer token")
    void protectedEndpointAcceptsValidToken() throws Exception {
        mvc().perform(post("/api/v1/retrieve")
                        .with(jwt().jwt(j -> j.claim("realm_access", java.util.Map.of("roles", java.util.List.of("gateway-user")))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"anything\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator health endpoint stays accessible without a Bearer token")
    void actuatorHealthStaysOpen() throws Exception {
        mvc().perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
