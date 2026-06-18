package com.org.chunking.strategy;

public class UnknownChunkingStrategyException extends RuntimeException {

    /**
     * Builds the exception message listing the unrecognized {@code name} and the available
     * strategy names.
     */
    public UnknownChunkingStrategyException(String name, Iterable<String> available) {
        super("Unknown chunking strategy '" + name + "'. Available: " + available);
    }
}
