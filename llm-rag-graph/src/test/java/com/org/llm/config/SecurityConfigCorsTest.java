package com.org.llm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @DisplayName("CORS configuration defaults to empty allowed origins when none are configured")
    @Test
    void corsConfigurationDefaultsToEmptyOriginsWhenNoneConfigured() {
        SecurityProperties props = new SecurityProperties();
        props.setAllowedOrigins(List.of());
        SecurityConfig config = new SecurityConfig(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/graph/stats");
        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(cors.getAllowedOrigins()).isEmpty();
        assertThat(cors.getAllowedHeaders()).contains("Authorization");
    }

    @DisplayName("CORS configuration uses the configured allowed origins")
    @Test
    void corsConfigurationUsesConfiguredOrigins() {
        SecurityProperties props = new SecurityProperties();
        props.setAllowedOrigins(List.of("https://example.com"));
        SecurityConfig config = new SecurityConfig(props);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/graph/stats");
        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(cors.getAllowedOrigins()).containsExactly("https://example.com");
        assertThat(cors.getAllowedHeaders()).contains("Authorization");
    }
}
