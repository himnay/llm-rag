package com.org.generation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingPolicyTest {

    private final GroundingPolicy policy = new GroundingPolicy();

    @Test
    @DisplayName("Produces grounded instructions restricting answers to provided context")
    void withContextProducesGroundedInstructions() {
        String rules = policy.groundingRules(true);
        assertThat(rules).contains("ONLY the provided context");
        assertThat(rules).doesNotContain("No relevant context was retrieved");
    }

    @Test
    @DisplayName("Informs the user no relevant context was retrieved when context is absent")
    void withoutContextInformsUserOfNoInformation() {
        String rules = policy.groundingRules(false);
        assertThat(rules).contains("No relevant context was retrieved");
    }

    @Test
    @DisplayName("Includes a rule against using prior knowledge to prevent hallucination")
    void withContextRulesPreventHallucination() {
        String rules = policy.groundingRules(true);
        assertThat(rules).contains("Do not use prior knowledge");
    }

    @Test
    @DisplayName("Includes a rule requiring citations when context is provided")
    void withContextRulesRequireCitations() {
        String rules = policy.groundingRules(true);
        assertThat(rules).contains("citation");
    }
}
