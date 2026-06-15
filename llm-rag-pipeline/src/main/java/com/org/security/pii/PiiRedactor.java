package com.org.security.pii;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Regex-based PII detector and redactor. Applied to document text before chunking and embedding so
 * that sensitive data (emails, phone numbers, SSNs, credit card numbers, IP addresses) never lands
 * in the vector store.
 *
 * <p>Enable via {@code app.security.pii.enabled=true}. Disabled by default to avoid unintended
 * redaction in corpora that legitimately contain contact information.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiiRedactor {

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            Pattern.compile("\\b(?:\\d{4}[\\- ]?){3}\\d{4}\\b"),
            Pattern.compile("(?:^|\\s)(?:\\+?1[-.\\s]?)?(?:\\(?\\d{3}\\)?[-.\\s]?)?\\d{3}[-.\\s]?\\d{4}\\b"),
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    );

    private final PiiProperties properties;

    public String redact(String text) {
        if (!properties.isEnabled() || text == null || text.isBlank()) {
            return text;
        }
        String replacement = properties.getReplacement();
        String out = text;
        for (Pattern pattern : PATTERNS) {
            out = pattern.matcher(out).replaceAll(replacement);
        }
        if (!out.equals(text)) {
            log.debug("PII redacted from document text");
        }
        return out;
    }
}
