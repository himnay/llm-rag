package com.org.chunking.strategy;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * LLM-driven semantic chunking: asks the chat model (via {@link ChatClient} and the
 * {@code prompts/chunking-system.st} system prompt) to segment the text into coherent chunks,
 * delimited by {@code ---CHUNK---}. Opt-in (set {@code app.chunking.strategy=llm}) due to LLM cost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmChunkingStrategy extends AbstractChunkingStrategy {

    private static final String DELIMITER = "(?m)^\\s*---CHUNK---\\s*$";

    private final ChatClient chatClient;
    private String systemPrompt;

    @PostConstruct
    void init() {
        try {
            systemPrompt = StreamUtils.copyToString(
                    new ClassPathResource("prompts/chunking-system.st").getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load chunking-system.st", e);
        }
    }

    @Override
    public String name() {
        return "llm";
    }

    @Override
    public List<Chunk> chunk(IngestedDocument document) {
        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(document.content())
                    .call()
                    .content();
            List<String> segments = response == null || response.isBlank()
                    ? List.of(document.content())
                    : List.of(response.split(DELIMITER));
            return toChunks(document, segments);
        } catch (Exception e) {
            log.warn("LLM chunking failed ({}), falling back to single chunk", e.getMessage());
            return toChunks(document, List.of(document.content()));
        }
    }
}
