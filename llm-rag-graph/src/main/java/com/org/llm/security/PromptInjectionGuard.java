package com.org.llm.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Guards the RAG query endpoint against prompt-injection attacks by scanning incoming questions
 * against a configurable set of regex patterns before the graph context is retrieved.
 */
@Slf4j
@Component
public class PromptInjectionGuard {

    private final boolean enabled;
    private final List<Pattern> compiled;

    public PromptInjectionGuard(
            @Value("${app.security.injection-guard.enabled:true}") boolean enabled,
            @Value("${app.security.injection-guard.patterns:}") List<String> patterns) {
        this.enabled = enabled;
        List<String> effective = patterns.isEmpty() ? defaultPatterns() : patterns;
        this.compiled = effective.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    public boolean isSafe(String input) {
        if (!enabled || input == null || input.isBlank()) return true;
        for (Pattern p : compiled) {
            if (p.matcher(input).find()) {
                log.warn("INJECTION_GUARD | blocked | pattern={}", p.pattern());
                return false;
            }
        }
        return true;
    }

    private static List<String> defaultPatterns() {
        return List.of(
                "(?i)ignore\\s+(?:all\\s+)?(?:previous|prior|above|all)\\s+instructions?",
                "(?i)ignore\\s+your\\s+(system\\s+)?prompt",
                "(?i)forget\\s+(your|all|previous)\\s+(instructions?|context|training)",
                "(?i)disregard\\s+(all|previous|your)\\s+instructions?",
                "(?i)override\\s+(the\\s+)?(system|previous|all)\\s+(prompt|instructions?)",
                "(?i)you\\s+are\\s+now\\s+(?:DAN|an?\\s+AI\\s+without)",
                "(?i)act\\s+as\\s+if\\s+you\\s+(have\\s+no|are\\s+without)\\s+(restrictions?|rules?)",
                "(?i)(print|reveal|show|output|repeat|display)\\s+(your\\s+)?(system\\s+)?prompt",
                "(?i)\\[\\s*SYSTEM\\s*\\]",
                "(?i)<\\s*system\\s*>",
                "(?i)jailbreak",
                "(?i)developer\\s+mode",
                "(?i)DAN\\s+mode"
        );
    }
}
