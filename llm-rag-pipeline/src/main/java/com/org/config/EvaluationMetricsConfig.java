package com.org.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class EvaluationMetricsConfig {

    private final ChatClient.Builder chatClientBuilder;

    /** Defines the fact checking evaluator bean. */
    @Bean
    public FactCheckingEvaluator factCheckingEvaluator() {
        return FactCheckingEvaluator.builder(chatClientBuilder).build();
    }

    /** Defines the relevancy evaluator bean. */
    @Bean
    public RelevancyEvaluator relevancyEvaluator() {
        return RelevancyEvaluator.builder().chatClientBuilder(chatClientBuilder).build();
    }
}
