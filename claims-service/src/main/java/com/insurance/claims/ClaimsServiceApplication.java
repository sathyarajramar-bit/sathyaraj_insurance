package com.insurance.claims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Claims against issued policies: registration (policy must cover the incident date), evidence
 * documents (verified against document-service), assessment, approval/rejection, settlement and
 * closure, with an explicit reopen process. Every transition is recorded in claim_status_history.
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class ClaimsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimsServiceApplication.class, args);
    }
}
