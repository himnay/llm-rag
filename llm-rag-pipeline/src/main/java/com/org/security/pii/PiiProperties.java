package com.org.security.pii;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.security.pii")
public class PiiProperties {

    /**
     * Detect and redact PII patterns (email, phone, SSN, credit card, IP) before embedding.
     */
    private boolean enabled = false;

    /**
     * Replacement token for each redacted PII span.
     */
    private String replacement = "[REDACTED]";
}
