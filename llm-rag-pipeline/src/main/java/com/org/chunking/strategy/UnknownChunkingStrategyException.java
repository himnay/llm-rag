package com.org.chunking.strategy;

public class UnknownChunkingStrategyException extends RuntimeException {

    public UnknownChunkingStrategyException(String name, Iterable<String> available) {
        super("Unknown chunking strategy '" + name + "'. Available: " + available);
    }
}
