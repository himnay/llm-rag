package com.org.ingestion.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * Thin wrapper over Tesseract (tess4j). Single source of truth for "is OCR available/enabled" and
 * for running OCR on a rasterized image. All native interaction is guarded: if the Tesseract native
 * library or language data is missing, OCR degrades to an empty string rather than failing ingestion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrService {

    private final OcrProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public int minCharsPerPage() {
        return properties.getMinCharsPerPage();
    }

    public int dpi() {
        return properties.getDpi();
    }

    /** OCRs a single image; returns extracted text, or empty when OCR is unavailable. */
    public String ocr(BufferedImage image) {
        if (!properties.isEnabled() || image == null) {
            return "";
        }
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setLanguage(properties.getLanguage());
            if (properties.getDataPath() != null && !properties.getDataPath().isBlank()) {
                tesseract.setDatapath(properties.getDataPath());
            }
            String text = tesseract.doOCR(image);
            return text == null ? "" : text.trim();
        } catch (Throwable t) {
            // Includes UnsatisfiedLinkError when libtesseract isn't installed.
            log.warn("OCR unavailable or failed ({}: {}) — skipping", t.getClass().getSimpleName(), t.getMessage());
            return "";
        }
    }
}
