package com.org.eval;

import java.util.List;

/**
 * Generation-quality report produced by {@link GenerationEvaluator}.
 *
 * @param question   the original user question
 * @param answer     the LLM-generated answer under evaluation
 * @param faithful   true if every claim in the answer is supported by the retrieved context
 * @param relevant   true if the answer addresses the user's question
 */
public record GenerationEvaluationReport(
        String question,
        String answer,
        boolean faithful,
        boolean relevant,
        List<String> contextSources) {
}
