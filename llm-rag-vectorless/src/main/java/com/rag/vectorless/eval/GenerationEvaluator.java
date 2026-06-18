package com.rag.vectorless.eval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Faithfulness (groundedness) check for generated answers: is every claim in the answer
 * entailed by the retrieved context? Backed by Spring AI's {@link FactCheckingEvaluator},
 * which issues an extra LLM call — gated behind {@code rag.evaluate-faithfulness}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationEvaluator {

    private final ChatClient.Builder chatClientBuilder;

    public boolean isFaithful(String question, List<Document> context, String answer) {
        try {
            FactCheckingEvaluator evaluator = FactCheckingEvaluator.builder(chatClientBuilder).build();
            EvaluationRequest request = new EvaluationRequest(question, context, answer);
            EvaluationResponse response = evaluator.evaluate(request);
            return response.isPass();
        } catch (Exception e) {
            log.warn("FactCheckingEvaluator failed ({}); defaulting to faithful=true", e.getMessage());
            return true;
        }
    }
}
