package com.org.lifecycle.command;

import com.org.lifecycle.KnowledgeLifecycleService;
import com.org.lifecycle.model.KnowledgeRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IngestCommand implements IngestionCommand {

    private final KnowledgeLifecycleService service;
    private final KnowledgeRequest request;

    @Override
    public void execute() throws java.io.IOException {
        service.ingest(request);
    }

    @Override
    public String describe() {
        return "IngestCommand[source=" + request.sourceType() + ", name=" + request.name() + "]";
    }
}
