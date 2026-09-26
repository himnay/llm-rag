package com.org.ingestion.job;

import com.org.ingestion.FileIngestionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class IngestionJobServiceTest {

    private final FileIngestionService files = mock(FileIngestionService.class);
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdown();
    }

    @Test
    @DisplayName("submit() returns while ingestion is still running, then the job completes and its temp copy is removed")
    void runsInTheBackgroundFromACopy() throws Exception {
        executor.setCorePoolSize(1);
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Path> ingestedFrom = new AtomicReference<>();
        doAnswer(invocation -> {
            Path path = invocation.getArgument(0);
            ingestedFrom.set(path);
            assertThat(Files.readString(path)).isEqualTo("hello");   // readable after the request ended
            release.await(5, TimeUnit.SECONDS);
            return null;
        }).when(files).ingestFile(any(), eq("notes.txt"));
        IngestionJobService service = new IngestionJobService(files, executor);

        String jobId = service.submit(new MockMultipartFile("file", "../../notes.txt", "text/plain", "hello".getBytes()));

        await().atMost(5, TimeUnit.SECONDS).until(() -> service.getJob(jobId).orElseThrow().status() == IngestionJob.Status.RUNNING);
        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> service.getJob(jobId).orElseThrow().status() == IngestionJob.Status.DONE);
        assertThat(service.getJob(jobId).orElseThrow().fileName()).isEqualTo("notes.txt");
        assertThat(ingestedFrom.get()).doesNotExist();
    }

    @Test
    @DisplayName("A full job queue is refused (503) and leaves no job or temp file behind")
    void fullQueueIsRejected() throws Exception {
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> release.await(5, TimeUnit.SECONDS)).when(files).ingestFile(any(), any());
        IngestionJobService service = new IngestionJobService(files, executor);
        service.submit(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()));

        assertThatThrownBy(() -> service.submit(new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes())))
                .isInstanceOf(TaskRejectedException.class);
        release.countDown();
    }
}
