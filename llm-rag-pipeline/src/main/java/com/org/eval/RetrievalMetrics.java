package com.org.eval;

import java.util.List;

/**
 * Pure, dependency-free retrieval-quality metric functions.
 *
 * <p>Each takes a per-rank relevance list {@code rel} where {@code rel.get(i)} is {@code true}
 * when the chunk at rank {@code i} (0-based) is relevant to the query. These mirror the
 * standard IR / RAGAS definitions and are unit-testable without any Spring context.</p>
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /** Precision@k = (relevant in top k) / k. */
    public static double precisionAtK(List<Boolean> rel, int k) {
        int n = Math.min(k, rel.size());
        if (n == 0) {
            return 0.0;
        }
        long hits = rel.subList(0, n).stream().filter(Boolean::booleanValue).count();
        return (double) hits / n;
    }

    /** Recall@k = (relevant in top k) / (total relevant that exist). */
    public static double recallAtK(List<Boolean> rel, int k, int totalRelevant) {
        if (totalRelevant <= 0) {
            return 0.0;
        }
        int n = Math.min(k, rel.size());
        long hits = rel.subList(0, n).stream().filter(Boolean::booleanValue).count();
        return Math.min(1.0, (double) hits / totalRelevant);
    }

    /** Reciprocal Rank = 1 / (rank of first relevant chunk); 0 if none relevant. */
    public static double reciprocalRank(List<Boolean> rel) {
        for (int i = 0; i < rel.size(); i++) {
            if (rel.get(i)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * RAGAS-style context precision — rank-aware average precision:
     * {@code Σ (Precision@i · rel(i)) / (#relevant)}. Rewards placing relevant chunks higher.
     */
    public static double contextPrecision(List<Boolean> rel, int totalRelevant) {
        if (totalRelevant <= 0) {
            return 0.0;
        }
        double sum = 0.0;
        int hits = 0;
        for (int i = 0; i < rel.size(); i++) {
            if (rel.get(i)) {
                hits++;
                sum += (double) hits / (i + 1);
            }
        }
        return sum / totalRelevant;
    }

    /** Hit Rate@k — 1.0 if at least one relevant chunk appears in the top-k results, else 0.0. */
    public static double hitRate(List<Boolean> rel, int k) {
        int n = Math.min(k, rel.size());
        for (int i = 0; i < n; i++) {
            if (rel.get(i)) return 1.0;
        }
        return 0.0;
    }

    /**
     * nDCG@k (Normalized Discounted Cumulative Gain) with binary relevance.
     * DCG = Σ rel(i) / log2(i+2); normalized by the ideal DCG (relevant docs placed first).
     */
    public static double nDcg(List<Boolean> rel, int k) {
        int n = Math.min(k, rel.size());
        long totalRelevant = rel.stream().filter(Boolean::booleanValue).count();
        if (totalRelevant == 0) return 0.0;

        double dcg = 0.0;
        for (int i = 0; i < n; i++) {
            if (rel.get(i)) dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        double idcg = 0.0;
        long idealHits = Math.min(totalRelevant, k);
        for (long i = 0; i < idealHits; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    /** Mean of a list of per-query metric values; 0 for an empty list. */
    public static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
