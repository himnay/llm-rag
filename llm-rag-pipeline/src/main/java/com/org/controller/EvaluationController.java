package com.org.controller;

import com.org.eval.EvaluationReport;
import com.org.eval.RetrievalEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * On-demand retrieval-quality evaluation. Runs the gold set ({@code eval/qrels.json}) through
 * the retrieval pipeline, returns the metrics report, and refreshes the {@code rag.eval.*}
 * Prometheus gauges. Requires a populated vector store.
 */
@RestController
@RequestMapping("/api/v1/admin/eval")
@RequiredArgsConstructor
class EvaluationController {

    private final RetrievalEvaluator evaluator;

    @PostMapping("/run")
    public EvaluationReport run(@RequestParam(name = "k", defaultValue = "10") int k) throws IOException {
        return evaluator.evaluate(k);
    }
}
