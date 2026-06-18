package com.org.llm.config;

import com.anthropic.client.AnthropicClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AnthropicConfigTest {

    @DisplayName("Builds an Anthropic client using the configured API key")
    @Test
    void buildsClientFromConfiguredApiKey() {
        AnthropicConfig config = new AnthropicConfig();
        ReflectionTestUtils.setField(config, "apiKey", "sk-test-key");

        AnthropicClient client = config.anthropicClient();

        assertThat(client).isNotNull();
    }

    @DisplayName("Falls back to environment-based client creation when API key is blank")
    @Test
    void fallsBackToEnvironmentWhenApiKeyBlank() {
        AnthropicConfig config = new AnthropicConfig();
        ReflectionTestUtils.setField(config, "apiKey", "");

        // No ANTHROPIC_API_KEY in this environment; the SDK lazily defers validation,
        // so the fallback still produces a (unusable until configured) client.
        AnthropicClient client = config.anthropicClient();

        assertThat(client).isNotNull();
    }
}
