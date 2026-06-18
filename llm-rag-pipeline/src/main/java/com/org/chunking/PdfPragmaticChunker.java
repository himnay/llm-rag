package com.org.chunking;

import com.org.chunking.model.Chunk;
import com.org.ingestion.model.IngestedDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdfPragmaticChunker {

    private static final int PDF_CHUNK_SIZE = 500;
    private static final int PDF_CHUNK_OVERLAP = 100;

    private final FixedSizeChunker fixedSizeChunker;

    /**
     * Fixed-size chunks a PDF document and tags each chunk's metadata with the PDF chunking
     * strategy and size/overlap used.
     */
    public List<Chunk> chunk(IngestedDocument document) {

        List<Chunk> rawChunks = fixedSizeChunker.chunk(document, PDF_CHUNK_SIZE, PDF_CHUNK_OVERLAP);

        return rawChunks.stream()
                .map(this::enrichPdfMetadata)
                .collect(Collectors.toList());
    }

    private Chunk enrichPdfMetadata(Chunk chunk) {
        Map<String, Object> enrichedMetadata = new HashMap<>(chunk.metadata());

        enrichedMetadata.put("sourceType", "PDF");
        enrichedMetadata.put("chunkStrategy", "PDF_PRAGMATIC_FIXED_SIZE");

        enrichedMetadata.put("chunkSize", PDF_CHUNK_SIZE);
        enrichedMetadata.put("chunkOverlap", PDF_CHUNK_OVERLAP);

        return new Chunk(
                chunk.source(),
                chunk.content(),
                enrichedMetadata,
                chunk.chunkIndex()
        );
    }
}
