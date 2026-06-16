package com.org.lifecycle.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published after a batch of documents has been chunked, enriched, and stored.
 */
@Getter
public class IngestionCompletedEvent extends ApplicationEvent {

    private final int documentCount;
    private final int chunkCount;

    public IngestionCompletedEvent(Object source, int documentCount, int chunkCount) {
        super(source);
        this.documentCount = documentCount;
        this.chunkCount = chunkCount;
    }
}
