package com.org.chunking.strategy;

import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * LLM-based selection of a chunking strategy for a document, used under {@code app.chunking.strategy=auto}
 * when {@code app.chunking.classifier.enabled=true}. Excludes {@code llm} (chunking strategy itself
 * costs an LLM call — not auto-selected by another LLM call) — {@code DB} sources never reach this
 * class since whole-row chunking is structural, decided unconditionally in {@code ChunkingOrchestrator}.
 * Uses Spring AI's structured-output API so the LLM is constrained to the closed enum of valid
 * strategies, rather than free text matched against a regex.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkingStrategyClassifier {

    private static final PromptTemplate PROMPT_TEMPLATE =
            new PromptTemplate(new ClassPathResource("prompts/chunking-strategy-classifier.st"));

    private final ChatClient chatClient;
    private final ChunkingProperties properties;

    /**
     * The closed set of strategies this classifier may pick (excludes {@code llm} and {@code db} —
     * see class javadoc).
     */
    enum ChunkingChoice {
        FIXED, RECURSIVE, TOKEN, SEMANTIC, MARKDOWN
    }

    /**
     * Structured-output target. Package-private so the test can construct one.
     */
    record StrategyDecision(ChunkingChoice strategy) {
    }

    /**
     * Asks the LLM to pick a strategy for this document. Returns empty if the call fails or the
     * model returns nothing, so the caller can fall back to the heuristic switch.
     */
    public Optional<String> classify(IngestedDocument document) {
        try {
            int sampleChars = properties.getClassifier().getSampleChars();
            String content = document.content();
            String excerpt = content.length() > sampleChars ? content.substring(0, sampleChars) : content;

            StrategyDecision decision = chatClient.prompt()
                    .user(PROMPT_TEMPLATE.render(Map.of("sourceType", document.source(), "excerpt", excerpt)))
                    .call()
                    .entity(StrategyDecision.class, spec -> spec.useProviderStructuredOutput());

            if (decision == null || decision.strategy() == null) {
                log.warn("Chunking classifier returned no strategy; falling back to heuristic");
                return Optional.empty();
            }
            return Optional.of(decision.strategy().name().toLowerCase(Locale.ROOT));
        } catch (Exception e) {
            log.warn("Chunking strategy classification failed ({}); falling back to heuristic", e.getMessage());
            return Optional.empty();
        }
    }
}
