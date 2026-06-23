package com.org.generation;

import com.org.chunking.model.Chunk;
import com.org.retrieval.model.RetrievalResult;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps Spring AI's {@link ContextualQueryAugmenter} to assemble the manual-mode RAG prompt:
 * formats retrieved chunks as numbered, citation-prefixed context blocks, injects them into a
 * grounding-rules template, and falls back to a fixed "no context" message when nothing was
 * retrieved (via {@code allowEmptyContext(false)}).
 */
@Component
public class PromptAugmenter {

    private static final String WITH_CONTEXT_TEMPLATE = """
            Answer the user's question using ONLY the provided context below.
            If the answer is not present in the context, say "I don't have enough information to answer that."
            Do not use prior knowledge. Do not make up information.
            Every factual statement MUST reference its source using the citation header shown before each context block.

            Context:
            ```
            {context}
            ```

            Question: {query}
            """;

    private static final String NO_CONTEXT_MESSAGE =
            "No relevant context was retrieved from the knowledge base. "
                    + "Inform the user that you do not have enough information to answer their question.";

    private final ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
            .promptTemplate(new PromptTemplate(WITH_CONTEXT_TEMPLATE))
            .emptyContextPromptTemplate(new PromptTemplate(NO_CONTEXT_MESSAGE))
            .allowEmptyContext(false)
            .documentFormatter(PromptAugmenter::formatContext)
            .build();

    /**
     * Renders the final user-turn message (rules + numbered/cited context + question, or the
     * fixed no-context message) along with the plain context text used by
     * {@code ContextSufficiencyJudge}.
     */
    public Augmented augment(String userQuestion, RetrievalResult retrievalResult) {
        List<Document> documents = retrievalResult.getChunks().orElse(List.of()).stream().map(PromptAugmenter::toDocument).toList();
        String context = formatContext(documents);
        Query augmented = queryAugmenter.augment(new Query(userQuestion), documents);
        return new Augmented(context, augmented.text());
    }

    private static Document toDocument(Chunk chunk) {
        return new Document(chunk.content(), new HashMap<>(chunk.metadata()));
    }

    private static String formatContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            sb.append("Context ").append(i + 1).append(":\n");
            sb.append(citationHeader(documents.get(i).getMetadata()));
            sb.append(documents.get(i).getText()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private static String citationHeader(Map<String, Object> metadata) {
        Object source = metadata.get("source");
        Object fileName = metadata.get("fileName");
        Object page = metadata.getOrDefault("page_number", metadata.get("page"));
        StringBuilder sb = new StringBuilder("[").append(source != null ? source : "SOURCE");
        if (fileName != null && !fileName.toString().isBlank()) {
            sb.append(": ").append(fileName);
        }
        if (page != null) {
            sb.append(", p.").append(page);
        }
        sb.append("]\n");
        return sb.toString();
    }

    /**
     * @param context    plain numbered/cited context text, with no grounding rules — fed to
     *                    {@code ContextSufficiencyJudge}
     * @param userMessage the full user-turn message to send to the LLM
     */
    public record Augmented(String context, String userMessage) {
    }
}
