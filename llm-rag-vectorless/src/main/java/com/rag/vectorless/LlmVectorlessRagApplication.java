package com.rag.vectorless;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmVectorlessRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmVectorlessRagApplication.class, args);
    }

}
