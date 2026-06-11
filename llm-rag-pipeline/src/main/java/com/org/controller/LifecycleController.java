package com.org.controller;

import com.org.ingestion.FileIngestionService;
import com.org.lifecycle.KnowledgeLifecycleService;
import com.org.lifecycle.model.KnowledgeRequest;
import com.org.web.validation.SupportedDocument;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/lifecycle")
@RequiredArgsConstructor
@Validated
class LifecycleController {

    private final KnowledgeLifecycleService knowledgeLifecycleService;
    private final FileIngestionService fileIngestionService;

    /** Upload a file (pdf/md/txt/json/docx/...) and ingest it through the full pipeline. */
    @PostMapping("/upload")
    public ResponseEntity<Object> upload(@RequestParam("file") @SupportedDocument MultipartFile file) throws Exception {
        fileIngestionService.ingestUpload(file);
        return ResponseEntity.ok(Map.of("status", "ingested", "fileName", file.getOriginalFilename()));
    }

    @PostMapping("/ingest")
    public ResponseEntity<Object> ingest(@Valid @RequestBody KnowledgeRequest request) throws Exception {
        knowledgeLifecycleService.ingest(request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Object> delete(@Valid @RequestBody KnowledgeRequest request) throws Exception {
        knowledgeLifecycleService.delete(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ingest-all")
    public ResponseEntity<Object> ingestAll() throws Exception {
        knowledgeLifecycleService.ingestAll();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<Object> deleteAll() {
        knowledgeLifecycleService.deleteAll();
        return ResponseEntity.ok().build();
    }

}
