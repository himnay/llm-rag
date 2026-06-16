package com.org.ingestion.db;

import com.org.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DatabaseIngestionServiceTest extends IntegrationTest {
    @Autowired
    private DatabaseIngestionService databaseIngestionService;

    @Test
    public void testDatabaseIngestionService() {
        databaseIngestionService.ingestFaqs();
        databaseIngestionService.ingestReleaseNotes();
        databaseIngestionService.ingestAnnouncements();
    }

}
