package com.org.generation;

import com.org.chunking.model.Chunk;
import com.org.retrieval.model.Citation;
import com.org.retrieval.model.RetrievalResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {

    private final ContextBuilder builder = new ContextBuilder();

    private static RetrievalResult resultOf(Chunk... chunks) {
        List<Citation> citations = List.of(); // no citations for simplicity
        return new RetrievalResult(List.of(chunks), citations);
    }

    private static RetrievalResult resultOf(List<Chunk> chunks, List<Citation> citations) {
        return new RetrievalResult(chunks, citations);
    }

    @Test
    void emptyResultProducesEmptyString() {
        assertThat(builder.build(new RetrievalResult(List.of(), List.of()))).isEmpty();
    }

    @Test
    void isEmptyReturnsTrueForNoChunks() {
        assertThat(builder.isEmpty(new RetrievalResult(List.of(), List.of()))).isTrue();
    }

    @Test
    void isEmptyReturnsFalseWhenChunksPresent() {
        Chunk chunk = new Chunk("PDF", "some content", Map.of(), 0);
        assertThat(builder.isEmpty(resultOf(chunk))).isFalse();
    }

    @Test
    void singleChunkAppearsNumberedWithContent() {
        Chunk chunk = new Chunk("PDF", "Annual leave entitlement is 25 days.", Map.of(), 0);
        String context = builder.build(resultOf(chunk));

        assertThat(context).contains("Context 1:");
        assertThat(context).contains("Annual leave entitlement is 25 days.");
    }

    @Test
    void citationHeaderAppearsBeforeChunkContent() {
        Chunk chunk = new Chunk("PDF", "Policy text here.", Map.of(), 0);
        Citation citation = new Citation("PDF", "hr-policy.pdf", "PDF#hr-policy.pdf", 3, 0, null);

        String context = builder.build(resultOf(List.of(chunk), List.of(citation)));

        assertThat(context).contains("[PDF: hr-policy.pdf, p.3]");
        assertThat(context).contains("Policy text here.");
    }

    @Test
    void citationWithoutFileNameOmitsColon() {
        Chunk chunk = new Chunk("WIKI", "Wiki text.", Map.of(), 0);
        Citation citation = new Citation("WIKI", null, "WIKI#page", null, 0, null);

        String context = builder.build(resultOf(List.of(chunk), List.of(citation)));

        assertThat(context).contains("[WIKI]");
        assertThat(context).doesNotContain("[WIKI:");
    }

    @Test
    void multipleChunksAreNumberedSequentially() {
        Chunk c1 = new Chunk("PDF", "First chunk.", Map.of(), 0);
        Chunk c2 = new Chunk("PDF", "Second chunk.", Map.of(), 1);
        Chunk c3 = new Chunk("WIKI", "Third chunk.", Map.of(), 0);

        String context = builder.build(resultOf(c1, c2, c3));

        assertThat(context).contains("Context 1:");
        assertThat(context).contains("Context 2:");
        assertThat(context).contains("Context 3:");
        assertThat(context).contains("First chunk.");
        assertThat(context).contains("Second chunk.");
        assertThat(context).contains("Third chunk.");
    }
}
