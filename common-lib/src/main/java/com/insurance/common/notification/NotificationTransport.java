package com.insurance.common.notification;

/**
 * How a {@link NotificationRequest} physically reaches notification-service. Two implementations:
 * {@link FeignNotificationTransport} (HTTP, the default) and {@code KafkaNotificationTransport}
 * ({@code messaging.kafka.enabled=true}). {@link NotificationPublisher} does not know which one it has.
 */
@FunctionalInterface
public interface NotificationTransport {

    void send(NotificationRequest request);

    /** Short name for log lines, e.g. "http" or "kafka". */
    default String name() {
        return "http";
    }
}
