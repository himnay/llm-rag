package com.org.ingestion.job;

import com.org.ingestion.FileIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages async file ingestion jobs. Callers submit a file, receive a {@code jobId}, and can
 * poll {@link #getJob(String)} for status — decoupling the HTTP response from the (potentially
 * slow) chunking + embedding + store pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionJobService {

    private final FileIngestionService fileIngestionService;
    private final ConcurrentHashMap<String, IngestionJob> jobs = new ConcurrentHashMap<>();

    /**
     * Submit a file upload for async processing. Returns a jobId immediately.
     */
    public String submit(MultipartFile file) {
        String jobId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        jobs.put(jobId, IngestionJob.pending(jobId, fileName));
        runAsync(jobId, file);
        log.info("Async ingestion job {} submitted for file={}", jobId, fileName);
        return jobId;
    }

    /**
     * Returns the current snapshot of the job, if it exists.
     */
    public Optional<IngestionJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Async("ingestionExecutor")
    void runAsync(String jobId, MultipartFile file) {
        jobs.put(jobId, jobs.get(jobId).running());
        try {
            fileIngestionService.ingestUpload(file);
            jobs.put(jobId, jobs.get(jobId).done());
            log.info("Async ingestion job {} completed", jobId);
        } catch (Exception e) {
            jobs.put(jobId, jobs.get(jobId).failed(e.getMessage()));
            log.error("Async ingestion job {} failed: {}", jobId, e.getMessage(), e);
        }
    }
}
