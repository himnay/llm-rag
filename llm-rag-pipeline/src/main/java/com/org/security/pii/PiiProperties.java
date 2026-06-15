package com.org.security.pii;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.pii")
public class PiiProperties {

    /** Detect and redact PII patterns (email, phone, SSN, credit card, IP) before embedding. */
    private boolean enabled = false;

    /** Replacement token for each redacted PII span. */
    private String replacement = "[REDACTED]";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getReplacement() { return replacement; }
    public void setReplacement(String replacement) { this.replacement = replacement; }
}
