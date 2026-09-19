package com.insurance.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Collects premiums for APPROVED proposals and policy renewals through a pluggable payment provider
 * (mock today). A successful payment is the trigger for policy issuance: the PaymentSuccessful event is
 * written to an outbox table in the same transaction and delivered to policy-service after commit, with
 * a retry job for deliveries that failed. Idempotency-Key guarantees a retried request never charges twice.
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
