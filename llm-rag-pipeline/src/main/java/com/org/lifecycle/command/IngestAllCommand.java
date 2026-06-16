package com.org.lifecycle.command;

import com.org.lifecycle.KnowledgeLifecycleService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IngestAllCommand implements IngestionCommand {

    private final KnowledgeLifecycleService service;

    @Override
    public void execute() throws java.io.IOException {
        service.ingestAll();
    }

    @Override
    public String describe() {
        return "IngestAllCommand";
    }
}
