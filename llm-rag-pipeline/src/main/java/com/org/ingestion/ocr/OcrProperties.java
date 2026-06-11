package com.org.ingestion.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OCR settings (prefix {@code app.ingestion.ocr}). Disabled by default: OCR requires the native
 * Tesseract library and is comparatively slow, so it is opt-in for corpora with scanned PDFs.
 */
@ConfigurationProperties(prefix = "app.ingestion.ocr")
public class OcrProperties {

    /** Master switch for OCR of scanned/image PDF pages. */
    private boolean enabled = false;

    /** Tesseract language pack(s), e.g. {@code eng} or {@code eng+deu}. */
    private String language = "eng";

    /** Optional tessdata directory; null/blank lets Tesseract use its default (TESSDATA_PREFIX). */
    private String dataPath = "";

    /** A page whose extracted text is shorter than this is treated as scanned and sent to OCR. */
    private int minCharsPerPage = 40;

    /** Render DPI for rasterizing PDF pages before OCR (higher = better accuracy, slower). */
    private int dpi = 300;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getDataPath() { return dataPath; }
    public void setDataPath(String dataPath) { this.dataPath = dataPath; }
    public int getMinCharsPerPage() { return minCharsPerPage; }
    public void setMinCharsPerPage(int minCharsPerPage) { this.minCharsPerPage = minCharsPerPage; }
    public int getDpi() { return dpi; }
    public void setDpi(int dpi) { this.dpi = dpi; }
}
