package com.org.llm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityPropertiesTest {

    @Test
    @DisplayName("Default property values are secure-by-default (auth disabled, no origins)")
    void defaultsAreSecureByDefault() {
        SecurityProperties props = new SecurityProperties();

        assertThat(props.isAuthEnabled()).isFalse();
        assertThat(props.getAllowedOrigins()).isEmpty();
    }

    @Test
    @DisplayName("Setters update fields and Lombok-generated equals/hashCode/toString reflect them")
    void settersUpdateFieldsAndLombokEqualityHolds() {
        SecurityProperties a = new SecurityProperties();
        a.setAuthEnabled(true);
        a.setAllowedOrigins(List.of("https://example.com"));

        SecurityProperties b = new SecurityProperties();
        b.setAuthEnabled(true);
        b.setAllowedOrigins(List.of("https://example.com"));

        assertThat(a.isAuthEnabled()).isTrue();
        assertThat(a.getAllowedOrigins()).containsExactly("https://example.com");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("https://example.com");
    }
}
