package com.org.ingestion;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Locations of the document corpora ingested by the pipeline. Defaults point at the bundled
 * sample data on the classpath; override with {@code app.ingestion.*} to point at an external
 * directory (e.g. {@code file:/data/pdfs/}).
 */
@Data
@ConfigurationProperties(prefix = "app.ingestion")
public class IngestionProperties {

    /**
     * Spring resource location (must end with '/') holding the PDF corpus.
     */
    private String pdfLocation = "classpath:data/pdfs/";

    /**
     * Spring resource location (must end with '/') holding the wiki markdown corpus.
     */
    private String wikiLocation = "classpath:data/wiki/";

    /**
     * PDF reader granularity: {@code page} (PagePdfDocumentReader) or {@code paragraph}.
     */
    private String pdfReader = "page";
}
