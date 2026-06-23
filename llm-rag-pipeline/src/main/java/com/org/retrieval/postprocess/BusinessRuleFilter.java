package com.org.retrieval.postprocess;

import com.org.chunking.model.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Domain visibility rules for DB-sourced chunks: hide RESTRICTED FAQs and announcements outside
 * their effective window. Previously inlined in {@code RetrievalService}; now a composable stage.
 */
@Slf4j
@Component
public class BusinessRuleFilter implements RetrievalPostProcessor {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public List<Chunk> process(String query, List<Chunk> chunks) {
        return chunks.stream().filter(this::isAllowed).collect(Collectors.toList());
    }

    private static String str(Object value) {
        return Objects.toString(value, "");
    }

    private boolean isAllowed(Chunk chunk) {
        Map<String, Object> metadata = chunk.metadata();
        if (!"DB".equals(str(metadata.get("source")))) {
            return true;
        }
        return switch (str(metadata.get("table"))) {
            case "announcements" -> isActiveAnnouncement(metadata);
            case "faqs" -> isPublicFaq(metadata);
            default -> true;
        };
    }

    private boolean isPublicFaq(Map<String, Object> metadata) {
        return !"RESTRICTED".equalsIgnoreCase(str(metadata.get("visibility")));
    }

    private boolean isActiveAnnouncement(Map<String, Object> metadata) {
        LocalDate today = LocalDate.now();
        String fromDate = str(metadata.get("effectiveFrom"));
        String tillDate = str(metadata.get("effectiveTo"));
        if (fromDate.isEmpty()) {
            return true;
        }
        try {
            LocalDate from = LocalDate.parse(fromDate, DATE_FORMAT);
            LocalDate to = tillDate.isEmpty() ? today.plusDays(1) : LocalDate.parse(tillDate, DATE_FORMAT);
            return !today.isBefore(from) && !today.isAfter(to);
        } catch (Exception e) {
            log.warn("Unparseable announcement dates (from='{}', to='{}') — allowing", fromDate, tillDate);
            return true;
        }
    }
}
