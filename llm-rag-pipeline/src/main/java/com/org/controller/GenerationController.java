package com.org.controller;

import com.org.dto.GenerateRequest;
import com.org.dto.GenerateResponse;
import com.org.generation.GenerationService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Full RAG generation endpoint (retrieve + augment + generate). Only registered when
 * {@code app.generation.enabled=true}; otherwise this service remains retrieval-only.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@ConditionalOnBean(GenerationService.class)
class GenerationController {

    private final GenerationService generationService;

    @Timed(value = "rag.generation", description = "End-to-end RAG generation latency", histogram = true)
    @PostMapping("/generate")
    public GenerateResponse generate(@Valid @RequestBody GenerateRequest request) {
        return generationService.generate(request);
    }
}
