package com.org.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.ai.openai")
class OpenAiProperties {

    private String apiKey = "";
}
