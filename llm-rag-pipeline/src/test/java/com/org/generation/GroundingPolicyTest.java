package com.org.generation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingPolicyTest {

    private final GroundingPolicy policy = new GroundingPolicy();

    @Test
    void withContextProducesGroundedInstructions() {
        String rules = policy.groundingRules(true);
        assertThat(rules).contains("ONLY the provided context");
        assertThat(rules).doesNotContain("No relevant context was retrieved");
    }

    @Test
    void withoutContextInformsUserOfNoInformation() {
        String rules = policy.groundingRules(false);
        assertThat(rules).contains("No relevant context was retrieved");
    }

    @Test
    void withContextRulesPreventHallucination() {
        String rules = policy.groundingRules(true);
        assertThat(rules).contains("Do not use prior knowledge");
    }

    @Test
    void withContextRulesRequireCitations() {
        String rules = policy.groundingRules(true);
        assertThat(rules).contains("citation");
    }
}
