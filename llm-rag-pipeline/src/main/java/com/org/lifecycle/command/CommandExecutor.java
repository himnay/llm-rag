package com.org.lifecycle.command;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Executes {@link IngestionCommand}s with audit logging and error wrapping.
 * Callers can pass any of the concrete commands (ingest, delete, rebuild) without
 * knowing their implementation details — the executor handles the ceremony.
 */
@Slf4j
@Component
public class CommandExecutor {

    public void execute(IngestionCommand command) throws java.io.IOException {
        long start = System.currentTimeMillis();
        log.info("Executing command: {}", command.describe());
        try {
            command.execute();
            log.info("Command {} completed in {}ms", command.describe(), System.currentTimeMillis() - start);
        } catch (java.io.IOException e) {
            log.error("Command {} failed after {}ms: {}", command.describe(),
                    System.currentTimeMillis() - start, e.getMessage(), e);
            throw e;
        }
    }
}
