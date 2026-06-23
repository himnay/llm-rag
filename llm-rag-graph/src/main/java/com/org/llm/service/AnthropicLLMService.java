package com.org.llm.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Wraps the Anthropic Java SDK to send graph-context-enriched prompts to Claude.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnthropicLLMService {

    private static final String SYSTEM_PROMPT = """
            You are an expert knowledge assistant with access to a rich organizational knowledge graph.
            The graph captures a 4-level company hierarchy:
              Company → Department → Team → Employee
            with cross-cutting relationships:
              Employee -[WORKS_ON]→ Project -[USES_TECHNOLOGY]→ Technology
              Employee -[REPORTS_TO]→ Employee  (management chain)
              Department -[COLLABORATES_WITH]→ Department
            
            When answering questions:
            1. Use ONLY the graph context provided — do not hallucinate facts.
            2. Cite specific nodes and relationships from the context.
            3. Explain relationship paths clearly (e.g., "Alice Chen is in the Backend Team, which is part of Engineering").
            4. If the context does not contain enough information, say so explicitly.
            5. Be concise but complete — include relevant structural details.
            """;
    private final AnthropicClient anthropicClient;
    @Value("${app.anthropic.model:claude-opus-4-8}")
    private String model;
    @Value("${app.anthropic.max-tokens:4096}")
    private int maxTokens;

    /**
     * Sends the question and graph context to Claude and returns its answer. Falls back to a
     * canned unavailability message via {@code answerFallback} if the circuit breaker is open.
     */
    @CircuitBreaker(name = "llm-rag-graph", fallbackMethod = "answerFallback")
    @Retry(name = "llm-rag-graph")
    public String answer(String question, String graphContext) {
        String userMessage = buildUserMessage(question, graphContext);
        log.debug("Sending request to Claude model={} maxTokens={}", model, maxTokens);

        try {
            Message response = anthropicClient.messages().create(
                    MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(maxTokens)
                            .thinking(ThinkingConfigAdaptive.builder().build())
                            .system(SYSTEM_PROMPT)
                            .addUserMessage(userMessage)
                            .build()
            );
            return extractText(response);
        } catch (Exception e) {
            log.error("Anthropic API call failed for question='{}': {}", question, e.getMessage(), e);
            throw new LlmCallException("LLM call to Claude failed: " + e.getMessage(), e);
        }
    }

    /**
     * LLM-as-judge groundedness ("faithfulness") check: asks Claude whether every factual claim in
     * {@code answer} is supported by {@code graphContext}. Costs one extra LLM call per invocation,
     * so callers must gate this behind a config flag (see {@code app.rag.evaluate-groundedness}).
     * <p>
     * This module talks to Claude via the raw Anthropic Java SDK and does not have Spring AI's
     * {@code ChatClient}/{@code FactCheckingEvaluator} on its classpath (the sibling llm-rag-pipeline
     * module uses Spring AI, but with the OpenAI starter, not Anthropic — there is no
     * spring-ai-anthropic starter available to reuse here). Rather than pull in Spring AI solely for
     * this one evaluator, we implement the same "is the answer entailed by the context" check as a
     * second direct Claude call with a constrained PASS/FAIL prompt.
     */
    public boolean checkGroundedness(String graphContext, String answer) {
        String prompt = """
                Context:
                %s

                Answer:
                %s

                Is every factual claim in the Answer supported by the Context above? \
                Respond with only one word: PASS if every claim is supported, or FAIL if any claim \
                is not supported or is unsupported speculation.
                """.formatted(graphContext, answer);

        try {
            Message response = anthropicClient.messages().create(
                    MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(16)
                            .addUserMessage(prompt)
                            .build()
            );
            String verdict = extractText(response).trim().toUpperCase();
            boolean pass = verdict.startsWith("PASS");
            log.debug("Groundedness check: {}", pass ? "PASS" : "FAIL");
            return pass;
        } catch (Exception e) {
            log.warn("Groundedness check failed ({}); defaulting to groundedness=true", e.getMessage());
            return true;
        }
    }

    @SuppressWarnings("unused")
    private String answerFallback(String question, String graphContext, Throwable t) {
        log.warn("AnthropicLLMService circuit breaker fallback for question='{}': {}", question, t.getMessage());
        return "The knowledge graph assistant is temporarily unavailable. Please try again in a moment.";
    }

    private String buildUserMessage(String question, String graphContext) {
        return """
                Here is the relevant knowledge graph context retrieved for your question:
                
                %s
                
                Question: %s
                
                Please answer the question based on the graph context above.
                """.formatted(graphContext, question);
    }

    private String extractText(Message message) {
        return message.content().stream()
                .filter(ContentBlock::isText)
                .map(block -> block.asText().text())
                .filter(s -> !s.isBlank())
                .findFirst()
                .orElse("No response generated.");
    }
}
