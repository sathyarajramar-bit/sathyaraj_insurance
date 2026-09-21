package com.insurance.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Receives NotificationRequests from every service, resolves the recipient (customer-service when the
 * producer did not know the email/phone), renders a template per event type and delivers through
 * channel providers (mock Email/SMS by default, SMTP e-mail via Gmail when notification.email.provider=SMTP; SES/Twilio later). Every message is stored with its
 * delivery status; a job retries FAILED ones. Idempotency key => the same event is never sent twice.
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
