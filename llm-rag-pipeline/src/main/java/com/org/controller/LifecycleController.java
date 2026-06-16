package com.org.controller;

import com.org.ingestion.FileIngestionService;
import com.org.ingestion.job.IngestionJob;
import com.org.ingestion.job.IngestionJobService;
import com.org.lifecycle.KnowledgeLifecycleService;
import com.org.lifecycle.command.CommandExecutor;
import com.org.lifecycle.model.KnowledgeRequest;
import com.org.web.validation.SupportedDocument;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/lifecycle")
class LifecycleController {

    private final KnowledgeLifecycleService knowledgeLifecycleService;
    private final FileIngestionService fileIngestionService;
    private final IngestionJobService ingestionJobService;
    private final CommandExecutor commandExecutor;

    /**
     * Synchronous upload — blocks until ingestion completes. Suitable for small files
     * and CLI/script callers. For large files use {@code POST /upload/async}.
     */
    @PostMapping("/upload")
    public ResponseEntity<Object> upload(@RequestParam("file") @SupportedDocument MultipartFile file) throws IOException {
        fileIngestionService.ingestUpload(file);
        return ResponseEntity.ok(Map.of("status", "ingested", "fileName", file.getOriginalFilename()));
    }

    /**
     * Async upload — returns a {@code jobId} immediately; the ingestion runs in the background.
     * Poll {@code GET /upload/{jobId}/status} to track progress.
     */
    @PostMapping("/upload/async")
    public ResponseEntity<Object> uploadAsync(@RequestParam("file") @SupportedDocument MultipartFile file) {
        String jobId = ingestionJobService.submit(file);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "status", "PENDING"));
    }

    /**
     * Returns the current status of an async ingestion job.
     */
    @GetMapping("/upload/{jobId}/status")
    public ResponseEntity<IngestionJob> jobStatus(@PathVariable String jobId) {
        return ingestionJobService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/ingest")
    public ResponseEntity<Object> ingest(@Valid @RequestBody KnowledgeRequest request) throws IOException {
        commandExecutor.execute(new com.org.lifecycle.command.IngestCommand(knowledgeLifecycleService, request));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Object> delete(@Valid @RequestBody KnowledgeRequest request) throws IOException {
        commandExecutor.execute(new com.org.lifecycle.command.DeleteCommand(knowledgeLifecycleService, request));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ingest-all")
    public ResponseEntity<Object> ingestAll() throws IOException {
        commandExecutor.execute(new com.org.lifecycle.command.IngestAllCommand(knowledgeLifecycleService));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<Object> deleteAll() throws IOException {
        commandExecutor.execute(knowledgeLifecycleService::deleteAll);
        return ResponseEntity.ok().build();
    }

}
