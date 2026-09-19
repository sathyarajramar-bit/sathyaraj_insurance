package com.insurance.policy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Issues and administers policies. A policy is only ever created from a PaymentSuccessful event (sent by
 * payment-service's outbox) and the service double-checks with payment-service that the payment really
 * is SUCCESS before issuing: "no policy before successful payment" is enforced here, not trusted.
 * Jobs expire policies past their end date and send renewal reminders 30/15/7 days before.
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class PolicyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyServiceApplication.class, args);
    }
}
