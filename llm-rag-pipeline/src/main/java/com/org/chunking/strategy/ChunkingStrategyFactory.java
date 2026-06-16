package com.org.chunking.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves a {@link ChunkingStrategy} by its {@link ChunkingStrategy#name()}.
 */
@Component
public class ChunkingStrategyFactory {

    private final Map<String, ChunkingStrategy> strategies;

    public ChunkingStrategyFactory(List<ChunkingStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(s -> s.name().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public ChunkingStrategy get(String name) {
        ChunkingStrategy strategy = strategies.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown chunking strategy '" + name + "'. Available: " + strategies.keySet());
        }
        return strategy;
    }

    public boolean has(String name) {
        return name != null && strategies.containsKey(name.toLowerCase(Locale.ROOT));
    }
}
