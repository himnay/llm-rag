package com.org.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * API-key authentication settings (prefix {@code app.security}).
 *
 * <p>Disabled by default for local/dev. In production set {@code app.security.auth-enabled=true}
 * and provision keys in the {@code api_keys} table (see {@link ApiKeyService}).</p>
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** When true, all {@code /api/**} routes require a valid {@link #header} value. */
    private boolean authEnabled = false;

    /** Header carrying the raw API key. */
    @NotBlank
    private String header = "X-API-Key";

    /** CORS allowed origins. Empty = CORS effectively disabled (no cross-origin browser access). */
    private List<String> allowedOrigins = List.of();

    private final RateLimit rateLimit = new RateLimit();

    /** Token-bucket rate limiting for {@code /api/**}, keyed by API key (or client IP). */
    public static class RateLimit {
        private boolean enabled = true;
        private int capacity = 120;
        private int refillPerMinute = 120;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillPerMinute() { return refillPerMinute; }
        public void setRefillPerMinute(int refillPerMinute) { this.refillPerMinute = refillPerMinute; }
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public void setAuthEnabled(boolean authEnabled) {
        this.authEnabled = authEnabled;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
