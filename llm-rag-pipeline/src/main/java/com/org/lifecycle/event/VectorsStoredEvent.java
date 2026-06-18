package com.org.lifecycle.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after vectors are written to the vector store.
 */
@Getter
public class VectorsStoredEvent extends ApplicationEvent {

    private final int chunkCount;
    /**
     * The identity whose vectors were (re-)written, or {@code null} for a full rebuild.
     */
    private final String identity;

    /**
     * Creates the event for a write of {@code chunkCount} vectors for the given identity (or
     * {@code null} for a full rebuild).
     */
    public VectorsStoredEvent(Object source, int chunkCount, String identity) {
        super(source);
        this.chunkCount = chunkCount;
        this.identity = identity;
    }
}
