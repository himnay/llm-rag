package com.org.llm.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class AnthropicConfig {

    @Value("${app.anthropic.api-key:}")
    private String apiKey;

    @Bean
    public AnthropicClient anthropicClient() {
        if (StringUtils.hasText(apiKey)) {
            return AnthropicOkHttpClient.builder()
                    .apiKey(apiKey)
                    .build();
        }
        // Falls back to ANTHROPIC_API_KEY environment variable
        return AnthropicOkHttpClient.fromEnv();
    }
}
