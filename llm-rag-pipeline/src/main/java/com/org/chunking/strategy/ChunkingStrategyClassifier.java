package com.org.chunking.strategy;

import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-based selection of a chunking strategy for a document, used under {@code app.chunking.strategy=auto}
 * when {@code app.chunking.classifier.enabled=true}. Excludes {@code llm} (chunking strategy itself
 * costs an LLM call — not auto-selected by another LLM call) — {@code DB} sources never reach this
 * class since whole-row chunking is structural, decided unconditionally in {@code ChunkingOrchestrator}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkingStrategyClassifier {

    private static final Set<String> VALID = Set.of("fixed", "recursive", "token", "semantic", "markdown");
    private static final Pattern STRATEGY_NAME = Pattern.compile("fixed|recursive|token|semantic|markdown");

    private static final String PROMPT = """
            Choose the single best chunking strategy for the following document, from this exact list:
            fixed, recursive, token, semantic, markdown.
            Respond with ONLY the strategy name, nothing else.

            Source type: %s
            Document excerpt:
            %s
            """;

    private final ChatClient chatClient;
    private final ChunkingProperties properties;

    /**
     * Asks the LLM to pick a strategy name for this document. Returns empty if the call fails or
     * the reply doesn't contain one of the valid names, so the caller can fall back to the
     * heuristic switch.
     */
    public Optional<String> classify(IngestedDocument document) {
        try {
            int sampleChars = properties.getClassifier().getSampleChars();
            String content = document.content();
            String excerpt = content.length() > sampleChars ? content.substring(0, sampleChars) : content;

            String reply = chatClient.prompt()
                    .user(PROMPT.formatted(document.source(), excerpt))
                    .call()
                    .content();

            String normalized = reply == null ? "" : reply.toLowerCase(Locale.ROOT);
            Matcher matcher = STRATEGY_NAME.matcher(normalized);
            if (matcher.find() && VALID.contains(matcher.group())) {
                return Optional.of(matcher.group());
            }
            log.warn("Chunking classifier returned unparseable strategy ('{}'); falling back to heuristic", reply);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Chunking strategy classification failed ({}); falling back to heuristic", e.getMessage());
            return Optional.empty();
        }
    }
}
