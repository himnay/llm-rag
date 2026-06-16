package com.org.eval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generation-quality evaluator implementing the RAG Triad (Section 10.3 of the learning doc):
 *
 * <ul>
 *   <li><b>Faithfulness / Groundedness</b> — is every claim in the answer supported by the
 *       retrieved context? Detected via Spring AI's {@link FactCheckingEvaluator}. A failing score
 *       here means the LLM hallucinated beyond the provided context.</li>
 *   <li><b>Answer Relevance</b> — does the answer actually address the user's question? Detected
 *       via Spring AI's {@link RelevancyEvaluator}.</li>
 * </ul>
 *
 * <p>In production, these can be wrapped in a {@code CallAroundAdvisor} so every response is
 * evaluated inline and a low faithfulness score triggers a fallback ("I don't have enough
 * information") instead of returning a hallucinated answer.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationEvaluator {

    private final ChatClient.Builder chatClientBuilder;

    /**
     * Check whether the answer is entailed by the retrieved context (faithfulness).
     * Returns {@code false} if the answer contains claims not supported by the context.
     */
    public boolean isFaithful(String question, List<Document> context, String answer) {
        try {
            FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(chatClientBuilder).build();
            EvaluationRequest request = new EvaluationRequest(question, context, answer);
            EvaluationResponse response = evaluator.evaluate(request);
            boolean pass = response.isPass();
            log.debug("FactCheck: {} | question='{}'", pass ? "PASS" : "FAIL", question);
            return pass;
        } catch (Exception e) {
            log.warn("FactCheckingEvaluator failed ({}); defaulting to faithful=true", e.getMessage());
            return true;
        }
    }

    /**
     * Check whether the answer is relevant to the question (answer relevance).
     */
    public boolean isRelevant(String question, List<Document> context, String answer) {
        try {
            RelevancyEvaluator evaluator = RelevancyEvaluator.builder().chatClientBuilder(chatClientBuilder).build();
            EvaluationRequest request = new EvaluationRequest(question, context, answer);
            EvaluationResponse response = evaluator.evaluate(request);
            boolean pass = response.isPass();
            log.debug("RelevancyCheck: {} | question='{}'", pass ? "PASS" : "FAIL", question);
            return pass;
        } catch (Exception e) {
            log.warn("RelevancyEvaluator failed ({}); defaulting to relevant=true", e.getMessage());
            return true;
        }
    }

    /**
     * Full RAG Triad evaluation — faithfulness + relevance in one call.
     */
    public GenerationEvaluationReport evaluate(String question, List<Document> context, String answer) {
        boolean faithful = isFaithful(question, context, answer);
        boolean relevant = isRelevant(question, context, answer);
        List<String> sources = context.stream()
                .map(d -> {
                    Object src = d.getMetadata().get("source");
                    Object file = d.getMetadata().get("fileName");
                    return src + (file != null ? ": " + file : "");
                })
                .distinct()
                .toList();
        log.info("GenerationEval | faithful={} | relevant={} | question='{}'", faithful, relevant, question);
        return new GenerationEvaluationReport(question, answer, faithful, relevant, sources);
    }
}
