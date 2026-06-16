package com.org.chunking.strategy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ChunkingStrategyFactory {

    private final List<ChunkingStrategy> strategyList;
    private Map<String, ChunkingStrategy> strategies;

    @PostConstruct
    void init() {
        strategies = strategyList.stream()
                .collect(Collectors.toMap(s -> s.name().toLowerCase(Locale.ROOT), Function.identity()));
    }

    public ChunkingStrategy get(String name) {
        ChunkingStrategy strategy = strategies.get(name == null ? "" : name.toLowerCase(Locale.ROOT));
        if (strategy == null) {
            throw new UnknownChunkingStrategyException(name, strategies.keySet());
        }
        return strategy;
    }

    public boolean has(String name) {
        return name != null && strategies.containsKey(name.toLowerCase(Locale.ROOT));
    }
}
