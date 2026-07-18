package com.org.ingestion.db;

import com.org.support.IntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DatabaseIngestionServiceTest extends IntegrationTest {
    @Autowired
    private DatabaseIngestionService databaseIngestionService;

    @Test
    @DisplayName("Ingests FAQs, release notes, and announcements from the database without throwing")
    void testDatabaseIngestionService() {
        databaseIngestionService.ingestFaqs();
        databaseIngestionService.ingestReleaseNotes();
        databaseIngestionService.ingestAnnouncements();
    }

}
