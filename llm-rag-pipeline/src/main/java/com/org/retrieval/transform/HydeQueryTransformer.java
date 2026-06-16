package com.org.retrieval.transform;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

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

    private static final String SYSTEM =
            "You are a document drafting assistant. Write a short, factual passage (2-4 sentences) "
                    + "that would be a plausible answer to the user's question, as if it came from an internal "
                    + "knowledge base or policy document. Do NOT say you don't know — write a plausible answer. "
                    + "Return ONLY the passage text.";

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
}
