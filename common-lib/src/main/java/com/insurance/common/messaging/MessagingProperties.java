package com.insurance.common.messaging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform messaging switches. {@code messaging.kafka.enabled=false} (the default) keeps every integration on
 * HTTP/Feign exactly as before; {@code true} moves the payment outbox and the notification fan-out to Kafka.
 * Topic names live here so producers and consumers can never drift apart.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "messaging.kafka")
public class MessagingProperties {

    /** Master switch: when false nothing Kafka-related is created (no topics, no listeners, no producer sends). */
    private boolean enabled = false;

    /** Partitions for topics created at startup (KafkaAdmin). Ordering is per key, so 3 is plenty locally. */
    private int partitions = 3;

    private final Topics topics = new Topics();

    private final Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Topics {
        /** payment-service -> policy-service: a premium was collected, issue/renew the policy. Key = paymentReference. */
        private String paymentSucceeded = "payment.succeeded";
        /** any service -> notification-service: send an e-mail/SMS. Key = idempotencyKey. */
        private String notificationRequested = "notification.requested";
    }

    @Getter
    @Setter
    public static class Consumer {
        /** Retries before a record is parked on the dead-letter topic ("&lt;topic&gt;.DLT"). */
        private int maxRetries = 3;
        /** First retry delay; doubles on every attempt. */
        private long initialBackoffMs = 1_000;
        private long maxBackoffMs = 10_000;
    }
}
