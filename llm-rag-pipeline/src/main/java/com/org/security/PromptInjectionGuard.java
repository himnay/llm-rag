package com.org.security;

import com.org.chunking.model.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Guards the prompt context against injection attacks embedded in retrieved documents. A malicious
 * document can carry instructions such as "ignore previous instructions" that get injected into the
 * LLM prompt through retrieval. This component scans each chunk and excludes any that contain
 * known injection patterns before they reach the generation layer.
 *
 * <p>Treat retrieved context as untrusted user input — this guard is always active.</p>
 */
@Slf4j
@Component
public class PromptInjectionGuard {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)ignore\\s+(?:all\\s+)?(?:previous|prior|above|all)\\s+instructions?"),
            Pattern.compile("(?i)ignore\\s+your\\s+(system\\s+)?prompt"),
            Pattern.compile("(?i)forget\\s+(your|all|previous)\\s+(instructions?|context|training)"),
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(?:DAN|an?\\s+AI\\s+without)"),
            Pattern.compile("(?i)disregard\\s+(all|previous|your)\\s+instructions?"),
            Pattern.compile("(?i)act\\s+as\\s+if\\s+you\\s+(have\\s+no|are\\s+without)\\s+(restrictions?|rules?)"),
            Pattern.compile("(?i)\\[\\s*SYSTEM\\s*\\]"),
            Pattern.compile("(?i)<\\s*system\\s*>"),
            Pattern.compile("(?i)jailbreak"),
            Pattern.compile("(?i)###\\s*(instruction|system|prompt)")
    );

    /**
     * Returns a new list with any chunks containing injection patterns removed.
     */
    public List<Chunk> filter(List<Chunk> chunks) {
        return chunks.stream()
                .filter(chunk -> {
                    boolean safe = isSafe(chunk.content());
                    if (!safe) {
                        log.warn("Prompt injection pattern detected in chunk source='{}' chunkIndex={} — excluded from context",
                                chunk.source(), chunk.chunkIndex());
                    }
                    return safe;
                })
                .collect(Collectors.toList());
    }

    public boolean isSafe(String text) {
        if (text == null || text.isBlank()) return true;
        return INJECTION_PATTERNS.stream().noneMatch(p -> p.matcher(text).find());
    }
}
