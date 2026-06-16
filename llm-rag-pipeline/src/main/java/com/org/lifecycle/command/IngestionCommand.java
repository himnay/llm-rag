package com.org.lifecycle.command;

/**
 * Command pattern for lifecycle operations. Each command encapsulates one unit of
 * knowledge-base work (ingest, delete, rebuild) that can be queued, logged, and retried
 * independently.
 */
@FunctionalInterface
public interface IngestionCommand {

    void execute() throws java.io.IOException;

    /**
     * Human-readable description used in audit logs and error messages.
     */
    default String describe() {
        return getClass().getSimpleName();
    }
}
