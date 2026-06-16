package com.org.ingestion.reader;

import com.org.ingestion.IngestionProperties;
import com.org.ingestion.ocr.OcrPdfAugmenter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads PDF files, applying page or paragraph segmentation and optional OCR for scanned pages.
 */
@Component
@RequiredArgsConstructor
class PdfReaderStrategy implements DocumentReaderStrategy {

    private final IngestionProperties properties;
    private final OcrPdfAugmenter ocrPdfAugmenter;

    @Override
    public DocumentType documentType() {
        return DocumentType.PDF;
    }

    @Override
    public List<Document> read(Resource resource) {
        List<Document> pages = "paragraph".equalsIgnoreCase(properties.getPdfReader())
                ? new ParagraphPdfDocumentReader(resource).get()
                : new PagePdfDocumentReader(resource).get();
        return ocrPdfAugmenter.augment(resource, pages);
    }
}
