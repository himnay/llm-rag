package com.org.security.pii;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiRedactorTest {

    private PiiRedactor redactorEnabled() {
        PiiProperties props = new PiiProperties();
        props.setEnabled(true);
        return new PiiRedactor(props);
    }

    private PiiRedactor redactorDisabled() {
        PiiProperties props = new PiiProperties();
        props.setEnabled(false);
        return new PiiRedactor(props);
    }

    @Test
    void whenDisabledTextIsUnchanged() {
        String text = "Contact alice@example.com or call 555-123-4567.";
        assertThat(redactorDisabled().redact(text)).isEqualTo(text);
    }

    @Test
    void redactsEmail() {
        String result = redactorEnabled().redact("Email me at alice@example.com for details.");
        assertThat(result).doesNotContain("alice@example.com").contains("[REDACTED]");
    }

    @Test
    void redactsSsn() {
        String result = redactorEnabled().redact("My SSN is 123-45-6789.");
        assertThat(result).doesNotContain("123-45-6789").contains("[REDACTED]");
    }

    @Test
    void redactsCreditCard() {
        String result = redactorEnabled().redact("Card number: 4111 1111 1111 1111.");
        assertThat(result).doesNotContain("4111 1111 1111 1111").contains("[REDACTED]");
    }

    @Test
    void safeTextUnchanged() {
        String safe = "The maximum annual leave entitlement is 25 days.";
        assertThat(redactorEnabled().redact(safe)).isEqualTo(safe);
    }

    @Test
    void nullAndBlankPassThrough() {
        PiiRedactor r = redactorEnabled();
        assertThat(r.redact(null)).isNull();
        assertThat(r.redact("")).isEqualTo("");
        assertThat(r.redact("   ")).isEqualTo("   ");
    }
}
