package com.org.retrieval;

import com.org.chunking.model.Chunk;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ChunkRankingComparator implements Comparator<Chunk> {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    /**
     * Orders chunks by source priority (PDF &gt; DB &gt; WIKI &gt; other), then DB table priority,
     * then recency (newer first) — used as a tie-breaker after relevance scoring.
     */
    @Override
    public int compare(Chunk a, Chunk b) {

        // 1. source priority
        int sourceCompare = Integer.compare(sourcePriority(a), sourcePriority(b));
        if (sourceCompare != 0) {
            return sourceCompare;
        }

        // 2. DB table priority
        if (isDbChunk(a) && isDbChunk(b)) {
            int tableCompare = Integer.compare(tablePriority(a), tablePriority(b));
            if (tableCompare != 0) {
                return tableCompare;
            }
        }

        // 3. Recency
        LocalDate dateA = extractRelevantDate(a);
        LocalDate dateB = extractRelevantDate(b);
        if (dateA != null && dateB != null) {
            return dateB.compareTo(dateA); // newer first
        }

        return 0;
    }

    private static String str(Object value) {
        return Objects.toString(value, "");
    }

    private LocalDate extractRelevantDate(Chunk chunk) {
        Map<String, Object> metadata = chunk.metadata();
        if (!"DB".equals(metadata.get("source"))) {
            return null;
        }
        String rawDate = switch (str(metadata.get("table"))) {
            case "release_notes" -> str(metadata.get("releaseDate"));
            case "announcements" -> str(metadata.get("effectiveFrom"));
            default -> "";
        };
        if (rawDate.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate, DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private int tablePriority(Chunk chunk) {
        return switch (str(chunk.metadata().get("table"))) {
            case "release_notes" -> 1;
            case "announcements" -> 2;
            case "faqs" -> 3;
            default -> 4;
        };
    }

    private boolean isDbChunk(Chunk chunk) {
        return "DB".equals(chunk.metadata().get("source"));
    }

    private int sourcePriority(Chunk chunk) {
        return switch (str(chunk.metadata().get("source"))) {
            case "PDF" -> 1;
            case "DB" -> 2;
            case "WIKI" -> 3;
            default -> 4;
        };
    }
}
