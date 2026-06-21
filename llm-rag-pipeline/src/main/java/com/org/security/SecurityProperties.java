package com.org.security;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * OAuth2/Keycloak authentication settings (prefix {@code app.security}).
 *
 * <p>Disabled by default for local/dev. In production set {@code app.security.auth-enabled=true}
 * so {@code /api/**} requires a valid Keycloak-issued Bearer token (see {@code SecurityConfig}).</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private final RateLimit rateLimit = new RateLimit();
    /**
     * When true, all {@code /api/**} routes require a valid Bearer token.
     */
    private boolean authEnabled = false;
    /**
     * CORS allowed origins. Empty = CORS effectively disabled (no cross-origin browser access).
     */
    private List<String> allowedOrigins = List.of();

    /**
     * Token-bucket rate limiting for {@code /api/**}, keyed by API key (or client IP).
     */
    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;
        private int capacity = 120;
        private int refillPerMinute = 120;
    }
}
