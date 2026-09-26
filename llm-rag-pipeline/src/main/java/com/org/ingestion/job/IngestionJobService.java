package com.org.ingestion.job;

import com.org.ingestion.FileIngestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Manages async file ingestion jobs. Callers submit a file, receive a {@code jobId}, and can
 * poll {@link #getJob(String)} for status — decoupling the HTTP response from the (potentially
 * slow) chunking + embedding + store pipeline.
 *
 * <p>The upload is copied to a temp file before {@link #submit} returns (the container deletes
 * its multipart storage when the request ends) and processed on {@code ingestionJobExecutor}.
 * This used to be an {@code @Async} method called from {@code submit} on the same object — a
 * self-invocation that bypasses the proxy, so every "async" upload ran on the request thread.</p>
 */
@Slf4j
@Service
public class IngestionJobService {

    /** Finished jobs stay pollable this long, then are dropped so the map can't grow forever. */
    static final Duration RETENTION = Duration.ofHours(1);

    private final FileIngestionService fileIngestionService;
    private final Executor jobExecutor;
    private final ConcurrentHashMap<String, IngestionJob> jobs = new ConcurrentHashMap<>();

    public IngestionJobService(FileIngestionService fileIngestionService,
                               @Qualifier("ingestionJobExecutor") Executor jobExecutor) {
        this.fileIngestionService = fileIngestionService;
        this.jobExecutor = jobExecutor;
    }

    /**
     * Submit a file upload for async processing. Returns a jobId immediately.
     *
     * @throws TaskRejectedException when the job queue is full (mapped to 503)
     */
    public String submit(MultipartFile file) throws IOException {
        evictFinishedJobs();
        String jobId = UUID.randomUUID().toString();
        String fileName = FileIngestionService.safeFileName(file);
        Path copy = FileIngestionService.copyToTempFile(file, fileName);
        jobs.put(jobId, IngestionJob.pending(jobId, fileName));
        try {
            jobExecutor.execute(() -> run(jobId, copy, fileName));
        } catch (TaskRejectedException e) {
            jobs.remove(jobId);
            deleteQuietly(copy);
            throw e;
        }
        log.info("Async ingestion job {} submitted for file={}", jobId, fileName);
        return jobId;
    }

    /**
     * Returns the current snapshot of the job, if it exists.
     */
    public Optional<IngestionJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    private void run(String jobId, Path copy, String fileName) {
        jobs.computeIfPresent(jobId, (id, job) -> job.running());
        try {
            fileIngestionService.ingestFile(copy, fileName);
            jobs.computeIfPresent(jobId, (id, job) -> job.done());
            log.info("Async ingestion job {} completed", jobId);
        } catch (Exception e) {
            jobs.computeIfPresent(jobId, (id, job) -> job.failed(e.getMessage()));
            log.error("Async ingestion job {} failed: {}", jobId, e.getMessage(), e);
        } finally {
            deleteQuietly(copy);
        }
    }

    private void evictFinishedJobs() {
        long cutoff = System.currentTimeMillis() - RETENTION.toMillis();
        jobs.values().removeIf(job -> job.finishedAtMillis() > 0 && job.finishedAtMillis() < cutoff);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temp upload {}: {}", path, e.getMessage());
        }
    }
}
