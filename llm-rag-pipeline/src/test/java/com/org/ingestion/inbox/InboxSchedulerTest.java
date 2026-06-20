package com.org.ingestion.inbox;

import com.org.ingestion.FileIngestionService;
import com.org.ingestion.reader.DocumentReaderFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InboxSchedulerTest {

    private final FileIngestionService fileIngestionService = mock(FileIngestionService.class);
    private final DocumentReaderFactory readerFactory = mock(DocumentReaderFactory.class);

    private InboxScheduler scheduler(Path inboxPath, Duration minAge) {
        InboxProperties properties = new InboxProperties();
        properties.setEnabled(true);
        properties.setPath(inboxPath.toString());
        properties.setMinAge(minAge);
        return new InboxScheduler(properties, fileIngestionService, readerFactory);
    }

    private static Path ageFile(Path file, Duration age) throws IOException {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
        return file;
    }

    @Test
    @DisplayName("Ingests a supported, sufficiently-aged file and moves it to processed/")
    void ingestsSupportedFileAndMovesToProcessed(@TempDir Path inbox) throws IOException {
        Path file = ageFile(Files.writeString(inbox.resolve("policy.pdf"), "content"), Duration.ofMinutes(1));
        when(readerFactory.supports("policy.pdf")).thenReturn(true);

        scheduler(inbox, Duration.ofSeconds(5)).scan();

        verify(fileIngestionService).ingestFile(file, "policy.pdf");
        assertThat(Files.exists(inbox.resolve("processed").resolve("policy.pdf"))).isTrue();
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    @DisplayName("Moves an unsupported file type to failed/ without attempting to ingest it")
    void unsupportedFileMovesToFailedWithoutIngesting(@TempDir Path inbox) throws IOException {
        Path file = ageFile(Files.writeString(inbox.resolve("video.mp4"), "binary"), Duration.ofMinutes(1));
        when(readerFactory.supports("video.mp4")).thenReturn(false);

        scheduler(inbox, Duration.ofSeconds(5)).scan();

        verify(fileIngestionService, never()).ingestFile(any(), anyString());
        assertThat(Files.exists(inbox.resolve("failed").resolve("video.mp4"))).isTrue();
    }

    @Test
    @DisplayName("Moves a file to failed/ when ingestion throws")
    void ingestionFailureMovesToFailed(@TempDir Path inbox) throws IOException {
        Path file = ageFile(Files.writeString(inbox.resolve("bad.pdf"), "content"), Duration.ofMinutes(1));
        when(readerFactory.supports("bad.pdf")).thenReturn(true);
        doThrow(new RuntimeException("parse error")).when(fileIngestionService).ingestFile(any(), anyString());

        scheduler(inbox, Duration.ofSeconds(5)).scan();

        assertThat(Files.exists(inbox.resolve("failed").resolve("bad.pdf"))).isTrue();
    }

    @Test
    @DisplayName("Leaves a file newer than min-age alone (still being written)")
    void leavesTooRecentFileAlone(@TempDir Path inbox) throws IOException {
        Path file = Files.writeString(inbox.resolve("fresh.pdf"), "content"); // just written, age ~0

        scheduler(inbox, Duration.ofMinutes(5)).scan();

        verify(fileIngestionService, never()).ingestFile(any(), anyString());
        assertThat(Files.exists(file)).isTrue();
        assertThat(Files.exists(inbox.resolve("processed").resolve("fresh.pdf"))).isFalse();
        assertThat(Files.exists(inbox.resolve("failed").resolve("fresh.pdf"))).isFalse();
    }
}
