package com.org.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Spring AI's {@code SafeGuardAdvisor} — blocks a request server-side if the user's prompt
 * contains any word from {@link #sensitiveWords}, returning {@link #failureResponse} instead of
 * calling the model. A content-moderation gate on the outgoing request, complementary to (not a
 * replacement for) {@link PromptInjectionGuard}, which scans <em>retrieved chunk content</em> for
 * injection patterns. Disabled with an empty word list by default — a no-op until configured.
 */
@Data
@ConfigurationProperties(prefix = "app.security.safeguard")
public class SafeGuardProperties {

    private boolean enabled = false;
    private List<String> sensitiveWords = List.of();
    private String failureResponse =
            "I'm unable to respond to that due to sensitive content. Could we rephrase or discuss something else?";
}
