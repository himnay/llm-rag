package com.org.ingestion.inbox;

import com.org.ingestion.FileIngestionService;
import com.org.ingestion.reader.DocumentReaderFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Watches a drop folder ({@code app.ingestion.inbox.path}) and ingests files that land there,
 * auto-detecting type by extension. Successfully ingested files move to {@code processed/}; failures
 * (and unsupported types) move to {@code failed/}. Only active when {@code app.ingestion.inbox.enabled=true}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ingestion.inbox", name = "enabled", havingValue = "true")
public class InboxScheduler {

    private final InboxProperties properties;
    private final FileIngestionService fileIngestionService;
    private final DocumentReaderFactory readerFactory;

    /**
     * Polls the inbox folder, ingesting each ready file and moving it to {@code processed/} on
     * success or {@code failed/} on error or unsupported type.
     */
    @Scheduled(fixedDelayString = "${app.ingestion.inbox.poll-interval:30s}")
    public void scan() {
        Path inbox = Path.of(properties.getPath());
        Path processed = inbox.resolve("processed");
        Path failed = inbox.resolve("failed");
        try {
            Files.createDirectories(processed);
            Files.createDirectories(failed);
        } catch (IOException e) {
            log.error("Could not prepare inbox directories under {}: {}", inbox, e.getMessage());
            return;
        }

        long minAgeMillis = properties.getMinAge().toMillis();
        try (Stream<Path> files = Files.list(inbox)) {
            List<Path> candidates = files.filter(Files::isRegularFile).toList();
            for (Path file : candidates) {
                String name = file.getFileName().toString();
                if (isTooRecent(file, minAgeMillis)) {
                    continue; // still being written
                }
                if (!readerFactory.supports(name)) {
                    log.warn("INBOX | unsupported file {} → failed/", name);
                    move(file, failed.resolve(name));
                    continue;
                }
                try {
                    fileIngestionService.ingestFile(file, name);
                    move(file, processed.resolve(name));
                    log.info("INBOX | ingested {} → processed/", name);
                } catch (Exception e) {
                    log.error("INBOX | failed to ingest {} → failed/ : {}", name, e.getMessage());
                    move(file, failed.resolve(name));
                }
            }
        } catch (IOException e) {
            log.error("INBOX | could not list {}: {}", inbox, e.getMessage());
        }
    }

    private boolean isTooRecent(Path file, long minAgeMillis) {
        try {
            return Files.getLastModifiedTime(file).toMillis() > Instant.now().toEpochMilli() - minAgeMillis;
        } catch (IOException e) {
            return true; // can't stat → treat as not ready
        }
    }

    private void move(Path from, Path to) {
        try {
            Files.move(from, uniqueTarget(to), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("INBOX | could not move {} → {}: {}", from, to, e.getMessage());
        }
    }

    private Path uniqueTarget(Path target) {
        if (!Files.exists(target)) {
            return target;
        }
        String name = target.getFileName().toString();
        return target.resolveSibling(System.currentTimeMillis() + "-" + name);
    }
}
