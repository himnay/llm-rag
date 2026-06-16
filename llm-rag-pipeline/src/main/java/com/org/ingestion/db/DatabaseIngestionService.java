package com.org.ingestion.db;

import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Ingests relational content into the RAG pipeline.
 *
 * <p><b>Template Method pattern</b>: {@link #ingestTable} encapsulates the
 * common SQL→iterate→wrap loop; each table method only supplies the query and a
 * row-to-{@link IngestedDocument} mapping function.</p>
 *
 * <p><b>Row-level identity</b>: each document carries an identity of the form
 * {@code DB#<table>#<id>} so the lifecycle service can detect and replace
 * individual rows without re-ingesting the entire table.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseIngestionService {

    private static final Map<String, Runnable> EMPTY = Map.of();

    private final JdbcTemplate jdbcTemplate;

    /**
     * Dispatch to the correct ingestion method, throwing on unknown table names.
     */
    public List<IngestedDocument> ingest(String tableName) {
        return switch (tableName.toLowerCase()) {
            case "faqs" -> ingestFaqs();
            case "release_notes" -> ingestReleaseNotes();
            case "announcements" -> ingestAnnouncements();
            default -> throw new IllegalArgumentException(
                    "Unknown database table for ingestion: '" + tableName
                            + "'. Supported tables: faqs, release_notes, announcements.");
        };
    }

    public List<IngestedDocument> ingestDatabaseContent() {
        List<IngestedDocument> docs = new ArrayList<>();
        docs.addAll(ingestFaqs());
        docs.addAll(ingestReleaseNotes());
        docs.addAll(ingestAnnouncements());
        return docs;
    }

    // ── Table-specific methods ────────────────────────────────────────────────

    public List<IngestedDocument> ingestFaqs() {
        return ingestTable(
                "SELECT id, question, answer, department, visibility FROM faqs",
                row -> {
                    String id = String.valueOf(row.get("id"));
                    String content = "Question: " + row.get("question") + "\nAnswer: " + row.get("answer");
                    return new IngestedDocument("DB", content, Map.of(
                            "table", "faqs",
                            "identity", "DB#faqs#" + id,
                            "id", row.get("id"),
                            "department", row.get("department"),
                            "visibility", row.get("visibility")));
                });
    }

    public List<IngestedDocument> ingestReleaseNotes() {
        return ingestTable(
                "SELECT id, version, summary, details, release_date FROM release_notes",
                row -> {
                    String id = String.valueOf(row.get("id"));
                    String content = "Version: " + row.get("version")
                            + "\nSummary: " + row.get("summary")
                            + "\nDetails: " + row.get("details");
                    return new IngestedDocument("DB", content, Map.of(
                            "table", "release_notes",
                            "identity", "DB#release_notes#" + id,
                            "id", row.get("id"),
                            "version", row.get("version"),
                            "releaseDate", row.get("release_date")));
                });
    }

    public List<IngestedDocument> ingestAnnouncements() {
        return ingestTable(
                "SELECT id, subject, body, category, effective_from, effective_to, source_type FROM announcements",
                row -> {
                    String id = String.valueOf(row.get("id"));
                    String content = "Subject: " + row.get("subject") + "\n" + row.get("body");
                    return new IngestedDocument("DB", content, Map.of(
                            "table", "announcements",
                            "identity", "DB#announcements#" + id,
                            "id", row.get("id"),
                            "category", row.get("category"),
                            "effectiveFrom", row.get("effective_from"),
                            "effectiveTo", row.get("effective_to") != null ? row.get("effective_to") : "",
                            "sourceType", row.get("source_type")));
                });
    }

    // ── Template Method ───────────────────────────────────────────────────────

    /**
     * Executes {@code sql}, maps each row via {@code rowMapper}, and returns the resulting
     * document list. Encapsulates the query→iterate→wrap boilerplate shared by all table methods.
     */
    private List<IngestedDocument> ingestTable(String sql,
                                               Function<Map<String, Object>, IngestedDocument> rowMapper) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        List<IngestedDocument> docs = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            docs.add(rowMapper.apply(row));
        }
        log.debug("Ingested {} row(s) via SQL: {}", docs.size(), sql);
        return docs;
    }
}
