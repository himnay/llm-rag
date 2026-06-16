package com.org.ingestion.ocr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OCR settings (prefix {@code app.ingestion.ocr}). Disabled by default: OCR requires the native
 * Tesseract library and is comparatively slow, so it is opt-in for corpora with scanned PDFs.
 */
@Data
@ConfigurationProperties(prefix = "app.ingestion.ocr")
public class OcrProperties {

    /**
     * Master switch for OCR of scanned/image PDF pages.
     */
    private boolean enabled = false;

    /**
     * Tesseract language pack(s), e.g. {@code eng} or {@code eng+deu}.
     */
    private String language = "eng";

    /**
     * Optional tessdata directory; null/blank lets Tesseract use its default (TESSDATA_PREFIX).
     */
    private String dataPath = "";

    /**
     * A page whose extracted text is shorter than this is treated as scanned and sent to OCR.
     */
    private int minCharsPerPage = 40;

    /**
     * Render DPI for rasterizing PDF pages before OCR (higher = better accuracy, slower).
     */
    private int dpi = 300;
}
