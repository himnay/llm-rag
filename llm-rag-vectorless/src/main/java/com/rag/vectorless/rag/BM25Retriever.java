package com.rag.vectorless.rag;

import com.rag.vectorless.config.RagProperties;
import com.rag.vectorless.dto.Chunk;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Vectorless document retriever using the BM25 ranking algorithm.
 * <p>
 * BM25 score for a document d given query q:
 * score(d,q) = Σ IDF(t) * tf(t,d)*(k1+1) / (tf(t,d) + k1*(1 - b + b*|d|/avgdl))
 * <p>
 * No embeddings, no vector database — pure keyword-based IR.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BM25Retriever {

    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "this", "that", "it", "not", "no", "as", "if"
    );

    private final RagProperties ragProperties;
    private final DocumentLoader documentLoader;

    private List<Chunk> chunks;
    private Map<String, double[]> termTf;
    private Map<String, Double> idf;
    private double[] docLengths;
    private double avgDocLength;

    @PostConstruct
    public void buildIndex() {
        chunks = documentLoader.getChunks();
        int n = chunks.size();

        if (n == 0) {
            termTf = Map.of();
            idf = Map.of();
            docLengths = new double[0];
            avgDocLength = 0;
            return;
        }

        termTf = new HashMap<>();
        docLengths = new double[n];
        double totalLength = 0;

        for (int i = 0; i < n; i++) {
            String[] terms = tokenize(chunks.get(i).text());
            docLengths[i] = terms.length;
            totalLength += terms.length;

            Map<String, Integer> counts = new HashMap<>();
            for (String term : terms) {
                counts.merge(term, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                termTf.computeIfAbsent(e.getKey(), k -> new double[n])[i] = e.getValue();
            }
        }

        avgDocLength = totalLength / n;

        idf = new HashMap<>(termTf.size());
        for (Map.Entry<String, double[]> e : termTf.entrySet()) {
            long df = Arrays.stream(e.getValue()).filter(v -> v > 0).count();
            idf.put(e.getKey(), Math.log((n - df + 0.5) / (df + 0.5) + 1.0));
        }

        log.info("BM25 index built: {} chunks, {} unique terms, avg length {}", n, idf.size(), String.format("%.1f", avgDocLength));
    }

    @Retry(name = "llm-vectorless")
    public List<Document> retrieve(String query) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        String[] queryTerms = tokenize(query);
        double[] scores = new double[chunks.size()];

        for (String term : queryTerms) {
            double[] tf = termTf.get(term);
            if (tf == null) continue;
            double idfScore = idf.getOrDefault(term, 0.0);

            for (int i = 0; i < chunks.size(); i++) {
                if (tf[i] == 0) continue;
                double dl = docLengths[i];
                double numerator = tf[i] * (K1 + 1.0);
                double denominator = tf[i] + K1 * (1.0 - B + B * dl / avgDocLength);
                scores[i] += idfScore * numerator / denominator;
            }
        }

        return IntStream.range(0, chunks.size())
                .filter(i -> scores[i] > 0)
                .boxed()
                .sorted((a, b) -> Double.compare(scores[b], scores[a]))
                .limit(ragProperties.topK())
                .map(i -> {
                    Chunk chunk = chunks.get(i);
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("source", chunk.source());
                    metadata.put("chunkIndex", chunk.chunkIndex());
                    metadata.put("score", Math.round(scores[i] * 1000.0) / 1000.0);
                    return new Document(chunk.text(), metadata);
                })
                .toList();
    }

    private String[] tokenize(String text) {
        return Arrays.stream(
                        text.toLowerCase(Locale.ROOT)
                                .replaceAll("[^a-z0-9\\s]", " ")
                                .split("\\s+"))
                .filter(t -> !t.isBlank() && t.length() > 2 && !STOP_WORDS.contains(t))
                .toArray(String[]::new);
    }
}
