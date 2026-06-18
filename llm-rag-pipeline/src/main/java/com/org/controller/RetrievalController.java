package com.org.controller;

import com.org.dto.RetrieveRequest;
import com.org.retrieval.RetrievalService;
import com.org.retrieval.model.RetrievalResult;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retrieval API — the "R" of this RAG service. Returns the most relevant chunks for a
 * query from the OpenSearch vector store. Generation (the LLM call) is out of scope here and
 * is handled by downstream consumers (e.g. llm-gateway).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "RAG Pipeline", description = "Vector retrieval and generation endpoints for the RAG pipeline")
class RetrievalController {

    private final RetrievalService retrievalService;

    @Operation(summary = "Retrieve the most relevant document chunks for a query using vector similarity search")
    @PostMapping("/retrieve")
    @Timed(value = "rag.retrieval", description = "Vector retrieval turnaround time", histogram = true)
    /**
     * Retrieves the most relevant chunks for {@code request.query()}, using {@code topK} if
     * provided or the configured default otherwise.
     */
    public RetrievalResult retrieve(@Valid @RequestBody RetrieveRequest request) {
        return request.topK() != null
                ? retrievalService.retrieve(request.query(), request.topK())
                : retrievalService.retrieve(request.query());
    }
}
