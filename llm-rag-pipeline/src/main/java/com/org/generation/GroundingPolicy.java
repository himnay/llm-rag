package com.org.generation;

import org.springframework.stereotype.Component;

/**
 * Produces the grounding rules injected into the LLM user message. The rules instruct the model
 * to use only the provided context and to explicitly say "I don't know" when the answer is not
 * present — preventing hallucination by anchoring the model to retrieved evidence.
 */
@Component
public class GroundingPolicy {

    private static final String WITH_CONTEXT =
            """
                    Answer the user's question using ONLY the provided context below.
                    If the answer is not present in the context, say "I don't have enough information to answer that."
                    Do not use prior knowledge. Do not make up information.
                    Every factual statement MUST reference its source using the citation header shown before each context block.
                    """;

    private static final String NO_CONTEXT =
            "No relevant context was retrieved from the knowledge base. "
                    + "Inform the user that you do not have enough information to answer their question.";

    public String groundingRules(boolean hasContext) {
        return hasContext ? WITH_CONTEXT : NO_CONTEXT;
    }
}
