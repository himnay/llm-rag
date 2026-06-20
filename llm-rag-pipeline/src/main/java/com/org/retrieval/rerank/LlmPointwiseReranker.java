package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Pointwise LLM-as-judge reranking: the chat LLM grades each candidate's relevance to the query
 * independently on a 0–100 scale (stored normalized to 0..1). Strong zero-shot quality without a
 * dedicated rerank vendor, but costs <em>one LLM call per candidate</em> — cap the spend (and the
 * burst of concurrent calls) with {@code app.retrieval.rerank.top-n}. Grades are fetched in
 * parallel on virtual threads, so latency is one LLM round-trip instead of N. Candidates are
 * truncated to {@value #MAX_DOC_CHARS} chars to bound prompt cost; an unparseable/missing grade
 * scores 0 rather than failing the request. Uses Spring AI's structured-output API ({@code
 * .entity(...)} with {@code useProviderStructuredOutput()}) so the grade is parsed from a real
 * JSON response rather than regexed out of free text.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmPointwiseReranker implements Reranker {

    static final int MAX_DOC_CHARS = 1500;

    private static final String PROMPT = """
            You grade search results. Rate how relevant the document is to the query \
            on a scale of 0 (irrelevant) to 100 (perfectly answers it).

            Query: %s

            Document:
            %s
            """;

    private final ChatClient chatClient;

    /**
     * Structured-output target for {@link #grade}. Package-private so the test can construct one.
     */
    record RelevanceGrade(int score) {
    }

    static String truncate(String content) {
        return content.length() <= MAX_DOC_CHARS ? content : content.substring(0, MAX_DOC_CHARS);
    }

    @Override
    public RerankStrategy strategy() {
        return RerankStrategy.LLM_POINTWISE;
    }

    @Override
    public List<Chunk> rerank(String query, List<Chunk> chunks) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Double>> grades = chunks.stream()
                    .map(chunk -> executor.submit(() -> grade(query, chunk)))
                    .toList();
            for (int i = 0; i < chunks.size(); i++) {
                Reranker.score(chunks.get(i), grades.get(i).get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM pointwise reranking interrupted", e);
        } catch (ExecutionException e) {
            // Propagate so RerankingPostProcessor fails open (and trips its circuit breaker).
            throw new IllegalStateException("LLM pointwise grading failed: " + e.getCause().getMessage(), e.getCause());
        }
        List<Chunk> reranked = new ArrayList<>(chunks);
        reranked.sort(Comparator.comparingDouble(
                (Chunk c) -> com.org.retrieval.postprocess.RetrievalPostProcessor.score(c)).reversed());
        return reranked;
    }

    private double grade(String query, Chunk chunk) {
        RelevanceGrade grade = chatClient.prompt()
                .user(PROMPT.formatted(query, truncate(chunk.content())))
                .call()
                .entity(RelevanceGrade.class, spec -> spec.useProviderStructuredOutput());
        if (grade == null) {
            log.warn("LLM pointwise reranker returned no grade — scoring 0");
            return 0;
        }
        return Math.min(100, Math.max(0, grade.score())) / 100.0;
    }
}
