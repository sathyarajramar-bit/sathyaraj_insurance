package com.insurance.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Stores documents (policy schedules, claim evidence, KYC uploads) and their metadata.
 * Metadata lives in document_db; bytes live in a {@code DocumentStorage} (local file system today,
 * S3 tomorrow) addressed by a storage key. Business tables elsewhere only hold document ids.
 * No Feign clients: this service is a leaf.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
