package com.org.eval;

import java.util.List;

/**
 * Aggregate retrieval-quality report produced by {@link RetrievalEvaluator}.
 *
 * @param k               cut-off used for precision@k / recall@k
 * @param queries         number of evaluated queries
 * @param mrr             mean reciprocal rank
 * @param meanContextPrecision mean RAGAS-style context precision (a.k.a. MAP)
 * @param meanPrecisionAtK     mean precision@k
 * @param meanRecallAtK        mean (source-level) recall@k
 * @param perQuery        per-query breakdown
 */
public record EvaluationReport(
        int k,
        int queries,
        double mrr,
        double meanContextPrecision,
        double meanPrecisionAtK,
        double meanRecallAtK,
        List<QueryResult> perQuery) {

    /** Per-query metrics. */
    public record QueryResult(
            String query,
            int retrieved,
            int relevantSourcesFound,
            int relevantSourcesExpected,
            double precisionAtK,
            double recallAtK,
            double reciprocalRank,
            double contextPrecision) {
    }
}
