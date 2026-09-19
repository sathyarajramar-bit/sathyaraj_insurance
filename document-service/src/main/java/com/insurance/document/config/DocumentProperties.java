package com.insurance.document.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "document")
public class DocumentProperties {

    private Storage storage = new Storage();

    /** Content types accepted for uploads. */
    private List<String> allowedContentTypes = List.of("application/pdf", "image/jpeg", "image/png", "text/plain");

    private long maxSizeBytes = 10L * 1024 * 1024;

    @Getter
    @Setter
    public static class Storage {
        private String provider = "LOCAL";
        private String localDir = "./data/documents";
    }
}
