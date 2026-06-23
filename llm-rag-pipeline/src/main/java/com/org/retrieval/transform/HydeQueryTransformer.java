package com.org.retrieval.transform;

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
 * Hypothetical Document Embeddings (HyDE): asks the LLM to draft a short passage that would
 * answer the question, then uses that hypothetical passage as the retrieval query instead of the
 * raw question. Because the hypothetical answer uses the same vocabulary as actual source
 * documents, its embedding lands closer to the true results in vector space — especially effective
 * when the query vocabulary differs significantly from the corpus vocabulary.
 *
 * <p>The hypothetical text is passed as the query string to the vector store, which embeds it
 * internally. No manual embedding is required.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HydeQueryTransformer implements QueryTransformer {

    private static final String SYSTEM = loadResource("prompts/hyde-system.st");

    private final ChatClient chatClient;

    @Override
    public QueryTransformMode mode() {
        return QueryTransformMode.HYDE;
    }

    @Override
    public List<String> transform(String query) {
        try {
            String hypothetical = chatClient.prompt()
                    .system(SYSTEM)
                    .user(query)
                    .call()
                    .content();
            String result = (hypothetical == null || hypothetical.isBlank()) ? query : hypothetical.strip();
            log.debug("HyDE: generated hypothetical passage ({} chars) for query '{}'", result.length(), query);
            return List.of(result);
        } catch (Exception e) {
            log.warn("HyDE generation failed ({}), falling back to original query", e.getMessage());
            return List.of(query);
        }
    }

    private static String loadResource(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load " + path, e);
        }
    }
}
