package com.org.retrieval.postprocess;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Lightweight, embedding-free text similarity used by the near-duplicate and MMR processors.
 * Uses bag-of-words cosine over tokenized text — an approximation of semantic similarity that is
 * good enough for collapsing duplicates and diversifying results without re-embedding candidates.
 */
final class TextSimilarity {

    private static final Pattern TOKEN = Pattern.compile("[^\\p{Alnum}]+");

    private TextSimilarity() {
    }

    /**
     * Cosine similarity (0..1) of the token-frequency vectors of two strings.
     */
    static double cosine(String a, String b) {
        Map<String, Integer> va = termFrequencies(a);
        Map<String, Integer> vb = termFrequencies(b);
        if (va.isEmpty() || vb.isEmpty()) {
            return 0.0;
        }
        Set<String> terms = new HashSet<>(va.keySet());
        terms.addAll(vb.keySet());
        double dot = 0, na = 0, nb = 0;
        for (String t : terms) {
            int fa = va.getOrDefault(t, 0);
            int fb = vb.getOrDefault(t, 0);
            dot += (double) fa * fb;
            na += (double) fa * fa;
            nb += (double) fb * fb;
        }
        return (na == 0 || nb == 0) ? 0.0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static Map<String, Integer> termFrequencies(String text) {
        Map<String, Integer> tf = new HashMap<>();
        if (text == null || text.isBlank()) {
            return tf;
        }
        for (String token : TOKEN.split(text.toLowerCase(Locale.ROOT))) {
            if (!token.isBlank()) {
                tf.merge(token, 1, Integer::sum);
            }
        }
        return tf;
    }
}
