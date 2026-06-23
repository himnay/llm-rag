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
 * Step-back prompting: asks the LLM to formulate a more general version of the question that
 * retrieves broader background context. The step-back query is used for retrieval, giving the LLM
 * the conceptual grounding needed to answer a very specific question that depends on background
 * knowledge not likely to appear verbatim in the corpus.
 *
 * <p>Example: "What is the chargeback time limit for Visa debit cards in the UK?" →
 * "What are chargeback dispute resolution rules for debit card transactions?"</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StepBackQueryTransformer implements QueryTransformer {

    private static final String SYSTEM = loadResource("prompts/step-back-system.st");

    private final ChatClient chatClient;

    @Override
    public QueryTransformMode mode() {
        return QueryTransformMode.STEP_BACK;
    }

    @Override
    public List<String> transform(String query) {
        try {
            String stepBack = chatClient.prompt()
                    .system(SYSTEM)
                    .user(query)
                    .call()
                    .content();
            String result = (stepBack == null || stepBack.isBlank()) ? query : stepBack.strip();
            log.debug("StepBack: '{}' -> '{}'", query, result);
            return List.of(result);
        } catch (Exception e) {
            log.warn("StepBack transformation failed ({}), using original query", e.getMessage());
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
