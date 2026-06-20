package com.org.controller;

import com.org.eval.EvaluationReport;
import com.org.eval.GenerationEvaluationReport;
import com.org.eval.GenerationEvaluator;
import com.org.eval.RetrievalEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * On-demand evaluation endpoints.
 *
 * <ul>
 *   <li>{@code POST /run} — retrieval-quality evaluation: runs the gold set through the retrieval
 *       pipeline and returns MRR, Precision@k, Recall@k, Hit Rate@k, nDCG@k, context precision.</li>
 *   <li>{@code POST /run/generation} — generation-quality evaluation (RAG Triad): checks
 *       faithfulness and relevance of a provided answer against a provided context.</li>
 * </ul>
 *
 * <p>Requires a populated vector store. Refreshes the {@code rag.eval.*} Prometheus gauges.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/eval")
class EvaluationController {

    private final RetrievalEvaluator retrievalEvaluator;
    private final GenerationEvaluator generationEvaluator;

    /**
     * Runs the retrieval-quality evaluation (MRR, Precision/Recall/Hit Rate/nDCG@k, context
     * precision) over the gold set, retrieving the top {@code k} chunks per query.
     */
    @PostMapping("/run")
    public EvaluationReport run(@RequestParam(name = "k", defaultValue = "10") int k) throws IOException {
        return retrievalEvaluator.evaluate(k);
    }

    /**
     * Evaluate a single (question, context, answer) triple for faithfulness and relevance.
     * Useful for spot-checking a specific answer or wiring into a CI golden-set test.
     */
    @PostMapping("/run/generation")
    public GenerationEvaluationReport runGeneration(
            @RequestParam String question,
            @RequestParam String answer,
            @RequestParam(defaultValue = "") List<String> contextChunks) {
        List<Document> context = contextChunks.stream()
                .map(text -> new Document(text, Map.of("source", "eval")))
                .toList();
        return generationEvaluator.evaluate(question, context, answer);
    }
}
