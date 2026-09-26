package com.rag.vectorless.rag;

import com.rag.vectorless.config.RagProperties;
import com.rag.vectorless.dto.ChatResponse;
import com.rag.vectorless.dto.Citation;
import com.rag.vectorless.eval.GenerationEvaluator;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The shared "answer only from this context" Claude call behind both retrieval endpoints.
 *
 * <p>Lives in its own bean so the {@code llm-vectorless} circuit breaker actually wraps it: the
 * annotation used to sit on a method the controller called on itself, and a self-invocation never
 * passes through the Spring AOP proxy, so the breaker and its fallback were silently inactive.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroundedAnswerGenerator {

    private final ChatClient chatClient;
    private final GenerationEvaluator generationEvaluator;
    private final RagProperties ragProperties;

    /**
     * Asks the model to answer {@code question} from {@code context} only, optionally judging the
     * answer's faithfulness to {@code contextDocs}.
     */
    @CircuitBreaker(name = "llm-vectorless", fallbackMethod = "fallback")
    public ChatResponse answer(String context, List<Document> contextDocs, List<Citation> citations, String question) {
        String userMessage = """
                Use only the context below to answer the question.
                If the context does not contain the answer, say "I don't have information about that."

                Context:
                %s

                Question: %s
                """.formatted(context, question);

        String answer = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        Boolean faithful = null;
        if (ragProperties.evaluateFaithfulness()) {
            faithful = generationEvaluator.isFaithful(question, contextDocs, answer);
        }

        return ChatResponse.builder().answer(answer).citations(citations).faithful(faithful).build();
    }

    @SuppressWarnings("unused")
    private ChatResponse fallback(String context, List<Document> contextDocs, List<Citation> citations, String question, Throwable t) {
        log.warn("LLM circuit breaker triggered for question='{}': {}", question, t.getMessage());
        return ChatResponse.builder().answer("Service temporarily unavailable. Please try again in a moment.").citations(List.of()).faithful(null).build();
    }
}
