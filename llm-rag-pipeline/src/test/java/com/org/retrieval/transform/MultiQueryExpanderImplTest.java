package com.org.retrieval.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MultiQueryExpanderImplTest {

    private MultiQueryExpanderImpl expanderReturning(String llmResponse, int count) {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(llmResponse);
        return new MultiQueryExpanderImpl(chatClient, count);
    }

    @Test
    @DisplayName("Reports MULTI_QUERY as its query transform mode")
    void modeIsMultiQuery() {
        assertThat(new MultiQueryExpanderImpl(mock(ChatClient.class), 3).mode())
                .isEqualTo(QueryTransformMode.MULTI_QUERY);
    }

    @Test
    @DisplayName("Returns LLM-generated query variants and always includes the original query")
    void returnsVariantsAndAlwaysIncludesOriginal() {
        MultiQueryExpanderImpl expander = expanderReturning(
                "Annual leave entitlement rules?\nHow many days off per year?", 3);
        List<String> result = expander.transform("leave policy");

        assertThat(result).contains("leave policy");
        assertThat(result.size()).isGreaterThan(1);
    }

    @Test
    @DisplayName("Prepends the original query when it is not already among the variants")
    void originalQueryIsPrependedWhenNotAlreadyPresent() {
        MultiQueryExpanderImpl expander = expanderReturning("Variant A\nVariant B", 2);
        List<String> result = expander.transform("original");

        assertThat(result.get(0)).isEqualTo("original");
    }

    @Test
    @DisplayName("Does not duplicate the original query when it is already among the variants")
    void doesNotDuplicateOriginalWhenAlreadyInVariants() {
        MultiQueryExpanderImpl expander = expanderReturning("original\nVariant A", 2);
        List<String> result = expander.transform("original");

        long count = result.stream().filter("original"::equals).count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Limits the number of variants to the configured count")
    void limitsVariantsToConfiguredCount() {
        // LLM returns 5 lines but count=2 → at most 2 variants + 1 original = 3 total
        MultiQueryExpanderImpl expander = expanderReturning("A\nB\nC\nD\nE", 2);
        List<String> result = expander.transform("original");

        assertThat(result.size()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM response is blank")
    void fallsBackToOriginalOnBlankResponse() {
        MultiQueryExpanderImpl expander = expanderReturning("", 3);
        assertThat(expander.transform("my query")).containsExactly("my query");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM response is null")
    void fallsBackToOriginalOnNullResponse() {
        MultiQueryExpanderImpl expander = expanderReturning(null, 3);
        assertThat(expander.transform("my query")).containsExactly("my query");
    }

    @Test
    @DisplayName("Falls back to the original query when the LLM call throws")
    void fallsBackToOriginalOnLlmException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("timeout"));

        assertThat(new MultiQueryExpanderImpl(chatClient, 3).transform("q")).containsExactly("q");
    }

    @Test
    @DisplayName("Filters out blank lines from the LLM response when building variants")
    void filtersBlankLinesFromResponse() {
        MultiQueryExpanderImpl expander = expanderReturning("Variant A\n\n  \nVariant B", 3);
        List<String> result = expander.transform("original");

        assertThat(result).doesNotContain("").doesNotContain("  ");
    }
}
