package com.insurance.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private String currency = "INR";

    private Outbox outbox = new Outbox();

    @Getter
    @Setter
    public static class Outbox {
        /** Cron for redelivering PENDING outbox events; "-" disables. */
        private String cron = "0 * * * * *";
        private int maxAttempts = 10;
        /** Base delay between attempts; doubles each time (capped at 1 hour). */
        private long backoffSeconds = 30;
    }
}
