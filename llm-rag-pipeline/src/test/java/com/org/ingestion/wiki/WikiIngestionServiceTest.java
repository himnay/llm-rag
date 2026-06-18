package com.org.ingestion.wiki;

import com.org.support.IntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class WikiIngestionServiceTest extends IntegrationTest {

    @Autowired
    private WikiIngestionService wikiIngestionService;

    @Test
    @DisplayName("Ingests wiki files without throwing")
    void ingestWikiFiles_forLearning() throws Exception {
        wikiIngestionService.ingestWikiFiles();
    }
}
