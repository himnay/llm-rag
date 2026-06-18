package com.org.llm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    @DisplayName("Default property values are secure-by-default (auth disabled, default header, no origins)")
    @Test
    void defaultsAreSecureByDefault() {
        SecurityProperties props = new SecurityProperties();

        assertThat(props.isAuthEnabled()).isFalse();
        assertThat(props.getHeader()).isEqualTo("X-API-Key");
        assertThat(props.getAllowedOrigins()).isEmpty();
    }

    @DisplayName("Setters update fields and Lombok-generated equals/hashCode/toString reflect them")
    @Test
    void settersUpdateFieldsAndLombokEqualityHolds() {
        SecurityProperties a = new SecurityProperties();
        a.setAuthEnabled(true);
        a.setHeader("X-Custom-Key");
        a.setAllowedOrigins(List.of("https://example.com"));

        SecurityProperties b = new SecurityProperties();
        b.setAuthEnabled(true);
        b.setHeader("X-Custom-Key");
        b.setAllowedOrigins(List.of("https://example.com"));

        assertThat(a.isAuthEnabled()).isTrue();
        assertThat(a.getHeader()).isEqualTo("X-Custom-Key");
        assertThat(a.getAllowedOrigins()).containsExactly("https://example.com");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("X-Custom-Key");
    }
}
