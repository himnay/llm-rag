package com.org.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for the retrieval-quality metric math — no Spring context or infra required,
 * so these run as part of the normal build.
 */
class RetrievalMetricsTest {

    private static final double EPS = 1e-9;

    // ranked relevance: relevant at ranks 1 and 3 (0-based 0 and 2)
    private static final List<Boolean> REL = List.of(true, false, true, false, false);

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(EPS);
    }

    @Test
    @DisplayName("Computes precision at K for a ranked relevance list")
    void precisionAtK() {
        assertThat(RetrievalMetrics.precisionAtK(REL, 1)).isCloseTo(1.0, within());   // 1/1
        assertThat(RetrievalMetrics.precisionAtK(REL, 2)).isCloseTo(0.5, within());   // 1/2
        assertThat(RetrievalMetrics.precisionAtK(REL, 3)).isCloseTo(2.0 / 3, within()); // 2/3
        assertThat(RetrievalMetrics.precisionAtK(REL, 5)).isCloseTo(0.4, within());   // 2/5
    }

    @Test
    @DisplayName("Handles an empty list and a K larger than the list size for precision")
    void precisionHandlesEmptyAndOversizedK() {
        assertThat(RetrievalMetrics.precisionAtK(List.of(), 5)).isZero();
        // k larger than list size clamps to list size (2 relevant / 5 returned)
        assertThat(RetrievalMetrics.precisionAtK(REL, 100)).isCloseTo(0.4, within());
    }

    @Test
    @DisplayName("Computes recall at K against the total relevant count, capped at 1.0")
    void recallAtK() {
        assertThat(RetrievalMetrics.recallAtK(REL, 5, 4)).isCloseTo(0.5, within());  // 2 of 4
        assertThat(RetrievalMetrics.recallAtK(REL, 1, 4)).isCloseTo(0.25, within()); // 1 of 4
        assertThat(RetrievalMetrics.recallAtK(REL, 5, 0)).isZero();                  // no ground truth
        // never exceeds 1.0 even if more relevant retrieved than the declared total
        assertThat(RetrievalMetrics.recallAtK(REL, 5, 1)).isCloseTo(1.0, within());
    }

    @Test
    @DisplayName("Computes the reciprocal rank of the first relevant result, or zero if none")
    void reciprocalRank() {
        assertThat(RetrievalMetrics.reciprocalRank(REL)).isCloseTo(1.0, within());          // first hit rank 1
        assertThat(RetrievalMetrics.reciprocalRank(List.of(false, false, true))).isCloseTo(1.0 / 3, within());
        assertThat(RetrievalMetrics.reciprocalRank(List.of(false, false))).isZero();        // no hit
    }

    @Test
    @DisplayName("Scores context precision higher when relevant hits rank earlier")
    void contextPrecisionIsRankAware() {
        // hits at ranks 1 and 3 → (1/1 + 2/3) / 2 = 0.8333...
        assertThat(RetrievalMetrics.contextPrecision(REL, 2)).isCloseTo((1.0 + 2.0 / 3) / 2, within());
        // same number of hits but lower ranks scores worse
        List<Boolean> lower = List.of(false, false, true, true);
        assertThat(RetrievalMetrics.contextPrecision(lower, 2))
                .isLessThan(RetrievalMetrics.contextPrecision(REL, 2));
        assertThat(RetrievalMetrics.contextPrecision(REL, 0)).isZero();
    }

    @Test
    @DisplayName("Computes the mean of a list of values, returning zero for an empty list")
    void mean() {
        assertThat(RetrievalMetrics.mean(List.of(1.0, 0.0, 0.5))).isCloseTo(0.5, within());
        assertThat(RetrievalMetrics.mean(List.of())).isZero();
    }

    @Test
    @DisplayName("Computes hit rate at K, true only when a relevant result appears within K")
    void hitRate() {
        assertThat(RetrievalMetrics.hitRate(REL, 1)).isCloseTo(1.0, within()); // first result is relevant
        assertThat(RetrievalMetrics.hitRate(List.of(false, false, true), 2)).isZero(); // relevant only at rank 3
        assertThat(RetrievalMetrics.hitRate(List.of(false, false, true), 3)).isCloseTo(1.0, within());
        assertThat(RetrievalMetrics.hitRate(List.of(), 5)).isZero();
        assertThat(RetrievalMetrics.hitRate(List.of(false, false), 5)).isZero();
    }

    @Test
    @DisplayName("Computes normalized discounted cumulative gain, rewarding higher-ranked hits")
    void nDcg() {
        // Single relevant hit at rank 1: DCG = 1/log2(2) = 1, IDCG = 1, nDCG = 1.0
        assertThat(RetrievalMetrics.nDcg(List.of(true, false, false), 3)).isCloseTo(1.0, within());

        // Relevant at rank 1 and 3 (0-based 0 and 2), k=3
        // DCG = 1/log2(2) + 1/log2(4) = 1 + 0.5 = 1.5; IDCG = 1/log2(2) + 1/log2(3)
        double idcg = 1.0 + 1.0 / (Math.log(3) / Math.log(2));
        assertThat(RetrievalMetrics.nDcg(REL, 3)).isCloseTo(1.5 / idcg, within());

        // No relevant results → 0
        assertThat(RetrievalMetrics.nDcg(List.of(false, false, false), 3)).isZero();

        // Result further down ranks lower than result at top
        double topRank = RetrievalMetrics.nDcg(List.of(true, false), 2);
        double lowerRank = RetrievalMetrics.nDcg(List.of(false, true), 2);
        assertThat(topRank).isGreaterThan(lowerRank);
    }
}
