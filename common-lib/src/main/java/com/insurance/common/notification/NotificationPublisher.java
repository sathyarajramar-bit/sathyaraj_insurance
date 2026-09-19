package com.insurance.common.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

/**
 * Fire-and-forget delivery to notification-service, off the request thread.
 *
 * <p>Design: a notification is never part of a business transaction's contract. If notification-service
 * is down the customer still gets their quote/policy; the failure is logged (and notification-service's
 * own retry job handles failures on its side once the message arrived). This is the event boundary that a
 * Kafka producer would replace: same {@link NotificationRequest} payload, different transport.
 */
@Slf4j
public class NotificationPublisher {

    private final NotificationClient client;

    public NotificationPublisher(NotificationClient client) {
        this.client = client;
    }

    @Async
    public void publish(NotificationRequest request) {
        try {
            client.send(request);
            log.info("Notification {} sent for {} {}", request.eventType(), request.referenceType(), request.referenceNumber());
        } catch (Exception e) {
            log.error("Notification {} for {} {} could not be delivered: {}", request.eventType(), request.referenceType(),
                    request.referenceNumber(), e.toString());
        }
    }
}
