package com.org.llm.controller;

import com.org.llm.dto.RagRequest;
import com.org.llm.dto.RagResponse;
import com.org.llm.security.PromptInjectionGuard;
import com.org.llm.service.GraphRAGService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final GraphRAGService ragService;
    private final PromptInjectionGuard injectionGuard;

    /**
     * Answers a natural-language question using the Graph RAG pipeline.
     */
    @PostMapping("/query")
    @RateLimiter(name = "rag-query", fallbackMethod = "queryRateLimitFallback")
    public ResponseEntity<Object> query(@Valid @RequestBody RagRequest request) {
        if (!injectionGuard.isSafe(request.getQuestion())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Your request contains disallowed instructions and cannot be processed."));
        }
        return ResponseEntity.ok(ragService.query(request));
    }

    @SuppressWarnings("unused")
    ResponseEntity<Object> queryRateLimitFallback(RagRequest request, Throwable t) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Rate limit exceeded. Please try again later."));
    }
}
