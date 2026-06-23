package com.org.lifecycle.command;

import com.org.lifecycle.KnowledgeLifecycleService;
import com.org.lifecycle.model.KnowledgeRequest;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteCommand implements IngestionCommand {

    private final KnowledgeLifecycleService service;
    private final KnowledgeRequest request;

    @Override
    public void execute() {
        service.delete(request);
    }

    @Override
    public String describe() {
        return "DeleteCommand[source=" + request.getSourceType() + ", name=" + request.getName() + "]";
    }
}
