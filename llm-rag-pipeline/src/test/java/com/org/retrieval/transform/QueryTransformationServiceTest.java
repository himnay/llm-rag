package com.org.retrieval.transform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryTransformationServiceTest {

    @Test
    @DisplayName("NONE mode returns the original query unchanged")
    void noneModeReturnsOriginalQueryUnchanged() {
        QueryTransformationService service = new QueryTransformationService(List.of());
        assertThat(service.transform("my question", QueryTransformMode.NONE))
                .containsExactly("my question");
    }

    @Test
    @DisplayName("A null mode returns the original query unchanged")
    void nullModeReturnsOriginalQuery() {
        QueryTransformationService service = new QueryTransformationService(List.of());
        assertThat(service.transform("my question", null))
                .containsExactly("my question");
    }

    @Test
    @DisplayName("A mode with no registered transformer falls back to the original query")
    void unregisteredModeFallsBackToOriginalQuery() {
        // No transformer registered for REWRITE → graceful fallback
        QueryTransformationService service = new QueryTransformationService(List.of());
        assertThat(service.transform("my question", QueryTransformMode.REWRITE))
                .containsExactly("my question");
    }

    @Test
    @DisplayName("Delegates transformation to the transformer registered for the requested mode")
    void delegatesToRegisteredTransformer() {
        QueryTransformer transformer = mock(QueryTransformer.class);
        when(transformer.mode()).thenReturn(QueryTransformMode.REWRITE);
        when(transformer.transform("raw query")).thenReturn(List.of("cleaner query"));

        QueryTransformationService service = new QueryTransformationService(List.of(transformer));
        assertThat(service.transform("raw query", QueryTransformMode.REWRITE))
                .containsExactly("cleaner query");
    }

    @Test
    @DisplayName("MULTI_QUERY mode can return multiple query variants from the transformer")
    void multiQueryTransformerCanReturnMultipleVariants() {
        QueryTransformer transformer = mock(QueryTransformer.class);
        when(transformer.mode()).thenReturn(QueryTransformMode.MULTI_QUERY);
        when(transformer.transform("question")).thenReturn(List.of("question", "variant A", "variant B"));

        QueryTransformationService service = new QueryTransformationService(List.of(transformer));
        assertThat(service.transform("question", QueryTransformMode.MULTI_QUERY))
                .containsExactly("question", "variant A", "variant B");
    }

    @Test
    @DisplayName("With multiple transformers registered, the one matching the mode is selected")
    void multipleTransformersRegisteredSelectsCorrectOne() {
        QueryTransformer rewriter = mock(QueryTransformer.class);
        when(rewriter.mode()).thenReturn(QueryTransformMode.REWRITE);
        when(rewriter.transform("q")).thenReturn(List.of("rewritten"));

        QueryTransformer hyde = mock(QueryTransformer.class);
        when(hyde.mode()).thenReturn(QueryTransformMode.HYDE);
        when(hyde.transform("q")).thenReturn(List.of("hypothetical passage"));

        QueryTransformationService service = new QueryTransformationService(List.of(rewriter, hyde));

        assertThat(service.transform("q", QueryTransformMode.REWRITE)).containsExactly("rewritten");
        assertThat(service.transform("q", QueryTransformMode.HYDE)).containsExactly("hypothetical passage");
    }
}
