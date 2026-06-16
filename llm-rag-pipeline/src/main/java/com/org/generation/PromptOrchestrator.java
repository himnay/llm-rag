package com.org.generation;

import com.org.retrieval.RetrievalService;
import com.org.retrieval.model.RetrievalResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Assembles the full chat prompt for the manual-mode RAG pipeline (Section 7 pattern).
 *
 * <ol>
 *   <li>Retrieve relevant chunks via {@link RetrievalService}.</li>
 *   <li>Build the prompt context string from the retrieved chunks.</li>
 *   <li>Derive grounding rules based on whether context was found.</li>
 *   <li>Load the system instructions from {@code prompts/system-prompt.st}.</li>
 *   <li>Return a {@link ChatPrompt} that the generation service passes to the LLM.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptOrchestrator {

    private final RetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final GroundingPolicy groundingPolicy;
    private final String systemInstructions = loadSystemPrompt();

    private static String loadSystemPrompt() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("prompts/system-prompt.st").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load prompts/system-prompt.st", e);
        }
    }

    public ChatPrompt build(String userQuestion, int topK) {
        RetrievalResult retrievalResult = retrievalService.retrieve(userQuestion, topK);
        String context = contextBuilder.build(retrievalResult);
        boolean hasContext = !contextBuilder.isEmpty(retrievalResult);
        String rules = groundingPolicy.groundingRules(hasContext);
        log.debug("PromptOrchestrator: retrieved {} chunk(s), hasContext={}", retrievalResult.chunks().size(), hasContext);
        return new ChatPrompt(systemInstructions, context, rules, retrievalResult);
    }

    /**
     * Immutable prompt assembled by the orchestrator.
     */
    public record ChatPrompt(
            String systemInstructions,
            String context,
            String groundingRules,
            com.org.retrieval.model.RetrievalResult retrievalResult) {

        /**
         * Builds the user-turn message combining grounding rules, context, and the question.
         */
        public String toLlmUserMessage(String userQuestion) {
            return groundingRules + "\n\nContext:\n```\n" + context + "\n```\n\nQuestion: " + userQuestion;
        }
    }
}
