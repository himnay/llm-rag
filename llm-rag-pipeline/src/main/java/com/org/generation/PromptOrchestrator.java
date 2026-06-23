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
import java.util.List;

/**
 * Assembles the full chat prompt for the manual-mode RAG pipeline (Section 7 pattern).
 *
 * <ol>
 *   <li>Retrieve relevant chunks via {@link RetrievalService}.</li>
 *   <li>Augment the question with the retrieved context via {@link PromptAugmenter} (grounding
 *       rules, citation headers, and the no-context fallback).</li>
 *   <li>Load the system instructions from {@code prompts/system-prompt.st}.</li>
 *   <li>Return a {@link ChatPrompt} that the generation service passes to the LLM.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptOrchestrator {

    private final RetrievalService retrievalService;
    private final PromptAugmenter promptAugmenter;
    private final String systemInstructions = loadSystemPrompt();

    /**
     * Retrieves relevant chunks for the question and assembles the full {@link ChatPrompt}.
     */
    public ChatPrompt build(String userQuestion, int topK) {
        RetrievalResult retrievalResult = retrievalService.retrieve(userQuestion, topK);
        return rebuild(userQuestion, retrievalResult);
    }

    /**
     * Re-assembles the {@link ChatPrompt} from an already-known retrieval result (e.g. after the
     * injection guard has filtered unsafe chunks) without retrieving again.
     */
    public ChatPrompt rebuild(String userQuestion, RetrievalResult retrievalResult) {
        PromptAugmenter.Augmented augmented = promptAugmenter.augment(userQuestion, retrievalResult);
        log.debug("PromptOrchestrator: {} chunk(s), hasContext={}",
                retrievalResult.getChunks().orElse(List.of()).size(), !augmented.context().isEmpty());
        return new ChatPrompt(systemInstructions, augmented.context(), augmented.userMessage(), retrievalResult);
    }

    private static String loadSystemPrompt() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("prompts/system-prompt.st").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load prompts/system-prompt.st", e);
        }
    }

    /**
     * Assembled prompt: system instructions, plain context text (for the sufficiency judge), the
     * full user-turn message to send to the LLM, and the underlying retrieval result.
     */
    public record ChatPrompt(
            String systemInstructions,
            String context,
            String userMessage,
            RetrievalResult retrievalResult) {
    }
}
