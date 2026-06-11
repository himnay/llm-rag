package com.org.llm.controller;

import com.org.llm.dto.RagRequest;
import com.org.llm.dto.RagResponse;
import com.org.llm.service.GraphRAGService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final GraphRAGService ragService;

    @PostMapping("/query")
    public ResponseEntity<RagResponse> query(@Valid @RequestBody RagRequest request) {
        return ResponseEntity.ok(ragService.query(request));
    }
}
