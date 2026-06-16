package com.org.ingestion.inbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Drop-folder ingestion configuration ({@code app.ingestion.inbox.*}). Disabled by default.
 */
@Data
@ConfigurationProperties(prefix = "app.ingestion.inbox")
public class InboxProperties {

    /**
     * Enable the folder-watching scheduler.
     */
    private boolean enabled = false;

    /**
     * Directory to watch. Processed files move to {@code <path>/processed}, failures to {@code <path>/failed}.
     */
    private String path = "./ingest-inbox";

    /**
     * Polling interval.
     */
    private Duration pollInterval = Duration.ofSeconds(30);

    /**
     * Minimum age before a file is processed, to avoid picking up partially-written files.
     */
    private Duration minAge = Duration.ofSeconds(5);
}
