package com.org.security;

import com.org.chunking.model.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Guards the prompt pipeline against injection attacks on two surfaces:
 *
 * <ul>
 *   <li><b>User-query guard</b> ({@link #isQuerySafe}) — call this on the raw user question before
 *       any retrieval or generation starts; reject the request immediately if it returns
 *       {@code false}.</li>
 *   <li><b>Retrieved-chunk filter</b> ({@link #filter}) — call this on every batch of chunks
 *       returned by the vector store; malicious documents embedded in the knowledge base (indirect
 *       injection) are silently dropped rather than rejected, because a single poisoned chunk
 *       shouldn't prevent a legitimate answer from the remaining safe chunks.</li>
 * </ul>
 *
 * <p>Patterns are externalised in {@link InjectionGuardProperties}
 * ({@code app.security.injection-guard.patterns}) so new attack signatures can be added or tuned
 * in configuration without code changes.</p>
 *
 * <p>Treat retrieved context as untrusted user input — this guard is always active.</p>
 */
@Slf4j
@Component
public class PromptInjectionGuard {

    private final List<Pattern> compiledPatterns;
    private final boolean enabled;
    private final String blockMessage;

    public PromptInjectionGuard(InjectionGuardProperties properties) {
        this.enabled = properties.isEnabled();
        this.blockMessage = properties.getBlockMessage();
        this.compiledPatterns = properties.getPatterns().stream()
                .flatMap(regex -> {
                    try {
                        return java.util.stream.Stream.of(Pattern.compile(regex));
                    } catch (PatternSyntaxException ex) {
                        log.error("SECURITY | invalid injection pattern skipped | regex='{}' | error={}", regex, ex.getMessage());
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
        log.info("SECURITY | PromptInjectionGuard ready | patterns={} enabled={}", compiledPatterns.size(), enabled);
    }

    /**
     * Filters a retrieved chunk list, silently removing any chunk whose content matches a
     * known injection pattern. Used for indirect injection (poisoned knowledge base documents).
     */
    public List<Chunk> filter(List<Chunk> chunks) {
        return chunks.stream()
                .filter(chunk -> {
                    boolean safe = isSafe(chunk.content());
                    if (!safe) {
                        log.warn("SECURITY | Injection pattern in retrieved chunk source='{}' chunkIndex={} — excluded",
                                chunk.source(), chunk.chunkIndex());
                    }
                    return safe;
                })
                .collect(Collectors.toList());
    }

    /**
     * Returns {@code true} if the text matches none of the configured injection patterns.
     * A {@code null} or blank text is always considered safe.
     */
    public boolean isSafe(String text) {
        if (!enabled || text == null || text.isBlank()) return true;
        return compiledPatterns.stream().noneMatch(p -> p.matcher(text).find());
    }

    /**
     * Validates the user's direct query before it enters the pipeline.
     * Unlike {@link #filter}, which silently drops malicious chunks, this returns {@code false}
     * so the caller can reject the request with a visible error.
     */
    public boolean isQuerySafe(String userQuery) {
        boolean safe = isSafe(userQuery);
        if (!safe) {
            log.warn("SECURITY | Injection pattern detected in user query — rejecting request");
        }
        return safe;
    }

    /** The configured rejection message to return to the caller when {@link #isQuerySafe} returns {@code false}. */
    public String blockMessage() {
        return blockMessage;
    }
}
