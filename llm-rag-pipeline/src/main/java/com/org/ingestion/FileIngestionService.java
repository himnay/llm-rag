package com.org.ingestion;

import com.org.ingestion.model.IngestedDocument;
import com.org.ingestion.reader.DocumentReaderFactory;
import com.org.lifecycle.KnowledgeLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Ingests an arbitrary file (uploaded via REST or dropped in the inbox folder) by detecting its type
 * from the extension, reading it with the matching Spring AI reader, and running it through the
 * clean → chunk → enrich → store pipeline. Shared by the upload endpoint and the inbox scheduler.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileIngestionService {

    private final DocumentReaderFactory readerFactory;
    private final KnowledgeLifecycleService lifecycleService;

    /**
     * Ingest a multipart upload. File-type validation is declarative on the REST boundary
     * (see {@code @SupportedDocument}); the reader factory remains the final guard.
     */
    public void ingestUpload(MultipartFile file) throws Exception {
        String name = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload");
        Path tmp = Files.createTempFile("upload-", "-" + name);
        try {
            file.transferTo(tmp);
            ingestFile(tmp, name);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /**
     * Ingest a file on disk, using {@code displayName} for type detection + identity metadata.
     */
    public void ingestFile(Path path, String displayName) throws Exception {
        // Resource whose filename is the original name (so extension detection + metadata are correct),
        // while content is read from the actual path.
        FileSystemResource resource = new FileSystemResource(path.toFile()) {
            @Override
            public String getFilename() {
                return displayName;
            }
        };
        List<IngestedDocument> documents = readerFactory.read(resource);
        lifecycleService.ingestDocuments(documents);
        log.info("Ingested file {} ({} segment(s))", displayName, documents.size());
    }
}
