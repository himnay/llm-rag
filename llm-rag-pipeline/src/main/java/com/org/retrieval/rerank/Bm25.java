package com.org.retrieval.rerank;

import com.org.chunking.model.Chunk;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Okapi BM25 scoring over the in-memory candidate set (IDF computed against the candidates, not the
 * whole corpus — standard practice for second-stage lexical reranking). Shared by
 * {@link Bm25Reranker} and {@link RrfFusionReranker}.
 */
final class Bm25 {

    /**
     * Standard Okapi defaults: k1 = term-frequency saturation, b = length normalization.
     */
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Pattern TOKEN = Pattern.compile("[^\\p{Alnum}]+");

    private Bm25() {
    }

    /**
     * BM25 score per chunk, in candidate order.
     */
    static double[] scores(String query, List<Chunk> chunks) {
        List<String> queryTerms = tokenize(query).keySet().stream().toList();
        List<Map<String, Integer>> docTerms = chunks.stream().map(c -> tokenize(c.content())).toList();

        double avgLength = docTerms.stream().mapToInt(Bm25::length).average().orElse(1.0);
        int n = docTerms.size();

        double[] scores = new double[n];
        for (String term : queryTerms) {
            long docFreq = docTerms.stream().filter(d -> d.containsKey(term)).count();
            if (docFreq == 0) {
                continue;
            }
            double idf = Math.log(1 + (n - docFreq + 0.5) / (docFreq + 0.5));
            for (int i = 0; i < n; i++) {
                int tf = docTerms.get(i).getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                double norm = K1 * (1 - B + B * length(docTerms.get(i)) / avgLength);
                scores[i] += idf * (tf * (K1 + 1)) / (tf + norm);
            }
        }
        return scores;
    }

    private static int length(Map<String, Integer> termFrequencies) {
        return termFrequencies.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static Map<String, Integer> tokenize(String text) {
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
