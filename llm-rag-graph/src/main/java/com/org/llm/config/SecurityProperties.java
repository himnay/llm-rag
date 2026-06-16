package com.org.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    private boolean authEnabled = false;
    private String header = "X-API-Key";
    private List<String> allowedOrigins = List.of();
}
