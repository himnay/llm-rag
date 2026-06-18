package com.org.ingestion.pdf;

import com.org.support.IntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PdfIngestionServiceTest extends IntegrationTest {
    @Autowired
    private PdfIngestionService pdfIngestionService;

    @Test
    @DisplayName("Ingests PDF files without throwing")
    void ingestPdfs_forLearning() throws Exception {
        pdfIngestionService.ingestPdfs();
    }

}
