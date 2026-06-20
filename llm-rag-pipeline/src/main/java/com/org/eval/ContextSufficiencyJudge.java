package com.org.eval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Pre-generation LLM-as-judge: decides whether retrieved context is sufficient to answer a query,
 * so {@code GenerationService} can skip the final generation call (and return a canned response)
 * rather than risk an answer with no real support. Fails open (returns {@code true}, i.e. proceeds
 * to generation) on any LLM error — mirrors {@code GenerationEvaluator.isFaithful}'s default.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextSufficiencyJudge {

    private static final String PROMPT = """
            You are judging whether the CONTEXT below contains enough information to answer the QUESTION.
            Respond with ONLY one word: SUFFICIENT or INSUFFICIENT.

            QUESTION: %s

            CONTEXT:
            %s
            """;

    private final ChatClient chatClient;

    public boolean isSufficient(String query, String context) {
        if (context == null || context.isBlank()) {
            return false; // no context at all is unambiguous — no LLM call needed
        }
        try {
            String reply = chatClient.prompt()
                    .user(PROMPT.formatted(query, context))
                    .call()
                    .content();
            String normalized = reply == null ? "" : reply.toUpperCase(Locale.ROOT);
            // "SUFFICIENT" is a substring of "INSUFFICIENT" — check the negative first.
            boolean sufficient = !normalized.contains("INSUFFICIENT") && normalized.contains("SUFFICIENT");
            log.debug("ContextSufficiencyJudge: {} | query='{}'", sufficient ? "SUFFICIENT" : "INSUFFICIENT", query);
            return sufficient;
        } catch (Exception e) {
            log.warn("ContextSufficiencyJudge failed ({}); defaulting to sufficient=true", e.getMessage());
            return true;
        }
    }
}
