package com.org.ingestion.ocr;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OCR is opt-in and must degrade gracefully (never throw) when disabled or unavailable.
 */
class OcrServiceTest {

    @Test
    void disabledReturnsEmptyAndReportsNotEnabled() {
        OcrProperties props = new OcrProperties(); // enabled = false by default
        OcrService service = new OcrService(props);

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.ocr(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB))).isEmpty();
    }

    @Test
    void nullImageReturnsEmpty() {
        OcrProperties props = new OcrProperties();
        props.setEnabled(true);
        assertThat(new OcrService(props).ocr(null)).isEmpty();
    }
}
