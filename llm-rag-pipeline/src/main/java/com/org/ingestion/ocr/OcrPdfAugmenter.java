package com.org.ingestion.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills in text for scanned/image PDF pages. The page reader yields (near-)empty text for such
 * pages; this augmenter rasterize those pages with PDFBox and runs {@link OcrService} on them,
 * replacing the page content with the OCR'd text. No-op when OCR is disabled, and fully guarded so
 * a render/OCR failure never breaks ingestion of the text-based pages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrPdfAugmenter {

    private final OcrService ocrService;

    private static Integer intMeta(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Replaces near-empty page text with OCR'd text for scanned pages; returns {@code pages}
     * unchanged when OCR is disabled, there are no pages, or augmentation fails.
     */
    public List<Document> augment(Resource resource, List<Document> pages) {
        if (!ocrService.isEnabled() || pages.isEmpty()) {
            return pages;
        }
        try (PDDocument pdf = Loader.loadPDF(resource.getContentAsByteArray())) {
            PDFRenderer renderer = new PDFRenderer(pdf);
            List<Document> out = new ArrayList<>(pages.size());
            int ocrCount = 0;
            for (Document page : pages) {
                String text = page.getText();
                Integer pageNumber = intMeta(page, "page_number");
                if ((text != null && text.length() >= ocrService.minCharsPerPage())
                        || pageNumber == null
                        || pageNumber < 1 || pageNumber > pdf.getNumberOfPages()) {
                    out.add(page);
                    continue;
                }
                BufferedImage image = renderer.renderImageWithDPI(pageNumber - 1, ocrService.dpi());
                String ocr = ocrService.ocr(image);
                if (ocr.isBlank()) {
                    out.add(page);
                    continue;
                }
                Map<String, Object> metadata = new HashMap<>(page.getMetadata());
                metadata.put("ocr", true);
                out.add(new Document(ocr, metadata));
                ocrCount++;
            }
            if (ocrCount > 0) {
                log.info("OCR augmented {} scanned page(s)", ocrCount);
            }
            return out;
        } catch (Exception e) {
            log.warn("OCR augmentation failed ({}) — using text-extracted pages as-is", e.getMessage());
            return pages;
        }
    }
}
