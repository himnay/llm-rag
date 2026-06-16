package com.org.controller;

import com.org.dto.GenerateRequest;
import com.org.dto.GenerateResponse;
import com.org.generation.GenerationService;
import io.micrometer.core.annotation.Timed;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Full RAG generation endpoints (retrieve + augment + generate). Only registered when
 * {@code app.generation.enabled=true}; otherwise this service remains retrieval-only.
 *
 * <ul>
 *   <li>{@code POST /generate} — blocking, returns the full answer with citations.</li>
 *   <li>{@code POST /generate/stream} — SSE streaming; tokens arrive as they are generated.</li>
 * </ul>
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

    /**
     * Streaming variant — tokens arrive via Server-Sent Events as they are generated.
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateStream(@Valid @RequestBody GenerateRequest request) {
        return generationService.stream(request);
    }
}
