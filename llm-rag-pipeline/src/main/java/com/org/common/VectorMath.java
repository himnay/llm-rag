package com.org.common;

/**
 * Shared embedding arithmetic. Centralises cosine similarity so SemanticCacheService,
 * SemanticChunkingStrategy, and any future consumer do not each carry their own copy.
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * Cosine similarity of two float vectors in the range [0, 1].
     * Returns 0.0 when either vector is null, empty, or zero-magnitude.
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
