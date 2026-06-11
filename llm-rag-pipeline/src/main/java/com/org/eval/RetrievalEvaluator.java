package com.org.eval;

import tools.jackson.databind.ObjectMapper;
import com.org.chunking.model.Chunk;
import com.org.retrieval.RetrievalService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Offline retrieval-quality evaluator.
 *
 * <p>Loads a gold set ({@code eval/qrels.json}) of queries with labelled relevant sources,
 * runs each through {@link RetrievalService}, and computes <b>MRR</b>, <b>context precision</b>
 * (RAGAS-style), <b>precision@k</b> and (source-level) <b>recall@k</b>. The aggregate values are
 * also published as Micrometer gauges ({@code rag.eval.*}) so they can be charted in Grafana.</p>
 *
 * <p>Relevance is judged at <i>source</i> granularity: a chunk is relevant when one of the
 * query's labels is a case-insensitive substring of its {@code fileName} / {@code identity} /
 * {@code source} metadata. Recall is measured over the set of distinct labelled sources.</p>
 */
@Slf4j
@Service
public class RetrievalEvaluator {

    private static final String QRELS_PATH = "eval/qrels.json";

    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    /** Mutable holder backing the Micrometer gauges (updated on each evaluation run). */
    private final Gauges gauges = new Gauges();

    public RetrievalEvaluator(RetrievalService retrievalService,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
        Gauge.builder("rag.eval.mrr", gauges, g -> g.mrr).register(meterRegistry);
        Gauge.builder("rag.eval.context_precision", gauges, g -> g.contextPrecision).register(meterRegistry);
        Gauge.builder("rag.eval.precision_at_k", gauges, g -> g.precisionAtK).register(meterRegistry);
        Gauge.builder("rag.eval.recall_at_k", gauges, g -> g.recallAtK).register(meterRegistry);
    }

    @PostConstruct
    void logGoldSetSize() {
        try {
            log.info("Retrieval evaluator initialised with {} gold queries from {}",
                    loadGoldSet().size(), QRELS_PATH);
        } catch (Exception e) {
            log.warn("Could not load gold set {} at startup: {}", QRELS_PATH, e.getMessage());
        }
    }

    /** Loads the gold evaluation set from the classpath. */
    public List<QueryRelevance> loadGoldSet() throws IOException {
        try (InputStream in = new ClassPathResource(QRELS_PATH).getInputStream()) {
            return List.of(objectMapper.readValue(in, QueryRelevance[].class));
        }
    }

    /**
     * Runs the gold set through retrieval and computes the aggregate report.
     *
     * @param k precision@k / recall@k cut-off
     */
    public EvaluationReport evaluate(int k) throws IOException {
        List<QueryRelevance> goldSet = loadGoldSet();

        List<EvaluationReport.QueryResult> perQuery = new ArrayList<>();
        List<Double> rrs = new ArrayList<>();
        List<Double> cps = new ArrayList<>();
        List<Double> ps = new ArrayList<>();
        List<Double> rs = new ArrayList<>();

        for (QueryRelevance gold : goldSet) {
            List<Chunk> chunks = retrievalService.retrieve(gold.query()).chunks();

            List<Boolean> rel = new ArrayList<>(chunks.size());
            Set<String> relevantSourcesFound = new LinkedHashSet<>();
            for (int i = 0; i < chunks.size(); i++) {
                Optional<String> matched = matchedLabel(chunks.get(i), gold.relevantSources());
                rel.add(matched.isPresent());
                if (matched.isPresent() && i < k) {
                    relevantSourcesFound.add(matched.get().toLowerCase(Locale.ROOT));
                }
            }

            int totalRelevantChunks = (int) rel.stream().filter(Boolean::booleanValue).count();
            int expectedSources = gold.relevantSources().size();

            double precision = RetrievalMetrics.precisionAtK(rel, k);
            double recall = expectedSources == 0
                    ? 0.0 : (double) relevantSourcesFound.size() / expectedSources;
            double rr = RetrievalMetrics.reciprocalRank(rel);
            double cp = RetrievalMetrics.contextPrecision(rel, totalRelevantChunks);

            rrs.add(rr);
            cps.add(cp);
            ps.add(precision);
            rs.add(recall);

            perQuery.add(new EvaluationReport.QueryResult(
                    gold.query(), chunks.size(), relevantSourcesFound.size(), expectedSources,
                    precision, recall, rr, cp));
            log.info("EVAL | q='{}' | retrieved={} | P@{}={} | R@{}={} | RR={} | ctxPrec={}",
                    gold.query(), chunks.size(), k, precision, k, recall, rr, cp);
        }

        EvaluationReport report = new EvaluationReport(
                k, goldSet.size(),
                RetrievalMetrics.mean(rrs),
                RetrievalMetrics.mean(cps),
                RetrievalMetrics.mean(ps),
                RetrievalMetrics.mean(rs),
                perQuery);

        gauges.update(report);
        log.info("EVAL | aggregate | queries={} | MRR={} | ctxPrec={} | P@{}={} | R@{}={}",
                report.queries(), report.mrr(), report.meanContextPrecision(),
                k, report.meanPrecisionAtK(), k, report.meanRecallAtK());
        return report;
    }

    /** Returns the first label that matches the chunk's source metadata, if any. */
    private Optional<String> matchedLabel(Chunk chunk, List<String> labels) {
        String haystack = (str(chunk.metadata().get("fileName")) + "|"
                + str(chunk.metadata().get("identity")) + "|"
                + str(chunk.source())).toLowerCase(Locale.ROOT);
        return labels.stream()
                .filter(label -> !label.isBlank() && haystack.contains(label.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private static String str(Object value) {
        return Objects.toString(value, "");
    }

    /** Backing state for the {@code rag.eval.*} gauges. */
    private static final class Gauges {
        volatile double mrr;
        volatile double contextPrecision;
        volatile double precisionAtK;
        volatile double recallAtK;

        void update(EvaluationReport r) {
            this.mrr = r.mrr();
            this.contextPrecision = r.meanContextPrecision();
            this.precisionAtK = r.meanPrecisionAtK();
            this.recallAtK = r.meanRecallAtK();
        }
    }
}
