package com.org.web;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the shipped OpenAPI contract ({@code static/openapi.yaml}) is well-formed and that its
 * documented paths stay versioned under {@code /api/v1}. Catches spec drift without running a server.
 */
class OpenApiContractTest {

    @Test
    void openApiSpecIsValidAndVersioned() throws Exception {
        String spec;
        try (InputStream in = new ClassPathResource("static/openapi.yaml").getInputStream()) {
            spec = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ParseOptions options = new ParseOptions();
        options.setResolveFully(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(spec, null, options);

        assertThat(result.getMessages())
                .as("OpenAPI validation messages")
                .isEmpty();
        assertThat(result.getOpenAPI()).isNotNull();
        // Every documented application API path must be versioned (actuator paths are exempt).
        assertThat(result.getOpenAPI().getPaths().keySet())
                .filteredOn(path -> path.startsWith("/api"))
                .isNotEmpty()
                .allSatisfy(path -> assertThat(path).startsWith("/api/v1"));
    }
}
