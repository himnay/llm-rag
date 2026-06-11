package com.org.ingestion.pdf;
import com.org.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PdfIngestionServiceTest extends IntegrationTest {
    @Autowired
    private PdfIngestionService pdfIngestionService;

    @Test
    void ingestPdfs_forLearning() throws Exception {
        pdfIngestionService.ingestPdfs();
    }

}
