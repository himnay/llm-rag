package com.org.eval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Pre-generation LLM-as-judge: decides whether retrieved context is sufficient to answer a query,
 * so {@code GenerationService} can skip the final generation call (and return a canned response)
 * rather than risk an answer with no real support. Fails open (returns {@code true}, i.e. proceeds
 * to generation) on any LLM error — mirrors {@code GenerationEvaluator.isFaithful}'s default.
 *
 * <p>Uses Spring AI's structured-output API instead of matching "SUFFICIENT"/"INSUFFICIENT"
 * substrings in free text — as a side effect, {@code reasoning} is now available for logging.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextSufficiencyJudge {

    private static final String PROMPT = """
            You are judging whether the CONTEXT below contains enough information to answer the QUESTION.

            QUESTION: %s

            CONTEXT:
            %s
            """;

    private final ChatClient chatClient;

    /**
     * Structured-output target. Package-private so the test can construct one.
     */
    record SufficiencyVerdict(boolean sufficient, String reasoning) {
    }

    public boolean isSufficient(String query, String context) {
        if (context == null || context.isBlank()) {
            return false; // no context at all is unambiguous — no LLM call needed
        }
        try {
            SufficiencyVerdict verdict = chatClient.prompt()
                    .user(PROMPT.formatted(query, context))
                    .call()
                    .entity(SufficiencyVerdict.class, spec -> spec.useProviderStructuredOutput());
            if (verdict == null) {
                log.warn("ContextSufficiencyJudge returned no verdict; defaulting to sufficient=true");
                return true;
            }
            log.debug("ContextSufficiencyJudge: {} ({}) | query='{}'",
                    verdict.sufficient() ? "SUFFICIENT" : "INSUFFICIENT", verdict.reasoning(), query);
            return verdict.sufficient();
        } catch (Exception e) {
            log.warn("ContextSufficiencyJudge failed ({}); defaulting to sufficient=true", e.getMessage());
            return true;
        }
    }
}
