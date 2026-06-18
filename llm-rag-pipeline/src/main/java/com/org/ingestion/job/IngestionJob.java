package com.org.ingestion.job;

/**
 * Immutable snapshot of an async ingestion job's current state.
 */
public record IngestionJob(
        String jobId,
        Status status,
        String fileName,
        String errorMessage,
        long startedAtMillis,
        long finishedAtMillis) {

    /**
     * Creates a newly-submitted job in {@code PENDING} status, timestamped now.
     */
    public static IngestionJob pending(String jobId, String fileName) {
        return new IngestionJob(jobId, Status.PENDING, fileName, null, System.currentTimeMillis(), 0);
    }

    /**
     * Returns a copy of this job transitioned to {@code RUNNING}.
     */
    public IngestionJob running() {
        return new IngestionJob(jobId, Status.RUNNING, fileName, null, startedAtMillis, 0);
    }

    /**
     * Returns a copy of this job transitioned to {@code DONE}, timestamped now.
     */
    public IngestionJob done() {
        return new IngestionJob(jobId, Status.DONE, fileName, null, startedAtMillis, System.currentTimeMillis());
    }

    /**
     * Returns a copy of this job transitioned to {@code FAILED} with the given error message.
     */
    public IngestionJob failed(String error) {
        return new IngestionJob(jobId, Status.FAILED, fileName, error, startedAtMillis, System.currentTimeMillis());
    }

    public enum Status {PENDING, RUNNING, DONE, FAILED}
}
