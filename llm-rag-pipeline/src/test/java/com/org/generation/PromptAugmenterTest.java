package com.org.generation;

import com.org.chunking.model.Chunk;
import com.org.retrieval.model.Citation;
import com.org.retrieval.model.RetrievalResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PromptAugmenterTest {

    private final PromptAugmenter augmenter = new PromptAugmenter();

    private static RetrievalResult resultOf(Chunk... chunks) {
        return new RetrievalResult(List.of(chunks), List.of());
    }

    @Test
    @DisplayName("Falls back to the fixed no-context message when nothing was retrieved")
    void emptyRetrievalProducesNoContextMessage() {
        PromptAugmenter.Augmented augmented = augmenter.augment("What is the leave policy?", resultOf());

        assertThat(augmented.context()).isEmpty();
        assertThat(augmented.userMessage()).contains("No relevant context was retrieved");
    }

    @Test
    @DisplayName("Renders a single chunk as numbered context with its citation header")
    void singleChunkAppearsNumberedWithCitationHeader() {
        Chunk chunk = new Chunk("PDF", "Annual leave entitlement is 25 days.",
                Map.of("source", "PDF", "fileName", "hr-policy.pdf", "page", 3), 0);

        PromptAugmenter.Augmented augmented = augmenter.augment("How many leave days?", resultOf(chunk));

        assertThat(augmented.context()).contains("Context 1:");
        assertThat(augmented.context()).contains("[PDF: hr-policy.pdf, p.3]");
        assertThat(augmented.context()).contains("Annual leave entitlement is 25 days.");
    }

    @Test
    @DisplayName("Omits the colon in the citation header when no file name is present")
    void citationWithoutFileNameOmitsColon() {
        Chunk chunk = new Chunk("WIKI", "Wiki text.", Map.of("source", "WIKI"), 0);

        PromptAugmenter.Augmented augmented = augmenter.augment("query", resultOf(chunk));

        assertThat(augmented.context()).contains("[WIKI]");
        assertThat(augmented.context()).doesNotContain("[WIKI:");
    }

    @Test
    @DisplayName("Numbers multiple chunks sequentially in the built context")
    void multipleChunksAreNumberedSequentially() {
        Chunk c1 = new Chunk("PDF", "First chunk.", Map.of(), 0);
        Chunk c2 = new Chunk("PDF", "Second chunk.", Map.of(), 1);
        Chunk c3 = new Chunk("WIKI", "Third chunk.", Map.of(), 0);

        PromptAugmenter.Augmented augmented = augmenter.augment("query", resultOf(c1, c2, c3));

        assertThat(augmented.context()).contains("Context 1:", "Context 2:", "Context 3:");
        assertThat(augmented.context()).contains("First chunk.", "Second chunk.", "Third chunk.");
    }

    @Test
    @DisplayName("User message includes grounding rules, the rendered context, and the question")
    void userMessageIncludesRulesContextAndQuestion() {
        Chunk chunk = new Chunk("PDF", "Policy text here.", Map.of(), 0);

        PromptAugmenter.Augmented augmented = augmenter.augment("What is the policy?", resultOf(chunk));

        assertThat(augmented.userMessage()).contains("ONLY the provided context");
        assertThat(augmented.userMessage()).contains("Do not use prior knowledge");
        assertThat(augmented.userMessage()).contains("citation");
        assertThat(augmented.userMessage()).contains("Policy text here.");
        assertThat(augmented.userMessage()).contains("Question: What is the policy?");
    }

    @Test
    @DisplayName("Citations record is unused by the augmenter (header comes from chunk metadata directly)")
    void citationRecordIsNotRequired() {
        // Regression guard: the old ContextBuilder paired chunks with a positionally-aligned
        // Citation list, which could misalign after dedup. The augmenter reads headers straight
        // off each chunk's own metadata, so it behaves correctly even with no Citation objects.
        Chunk chunk = new Chunk("PDF", "content", Map.of("source", "PDF", "fileName", "doc.pdf"), 0);
        RetrievalResult result = new RetrievalResult(List.of(chunk), List.of(
                new Citation("UNRELATED", "other.pdf", "other", 99, 5, null)));

        PromptAugmenter.Augmented augmented = augmenter.augment("query", result);

        assertThat(augmented.context()).contains("[PDF: doc.pdf]");
    }
}
