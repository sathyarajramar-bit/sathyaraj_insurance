package com.insurance.notification.messaging;

import com.insurance.common.notification.NotificationRequest;
import com.insurance.notification.dto.NotificationResponse;
import com.insurance.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka consumer of {@code notification.requested} (group {@code notification-service}): the same
 * {@link NotificationRequest} every producer used to POST to {@code /api/notifications}, handled by the same
 * {@link NotificationService#accept} (idempotent on idempotencyKey + channel, so a redelivery sends nothing twice).
 * Records that keep failing end up on {@code notification.requested.DLT}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging.kafka", name = "enabled", havingValue = "true")
public class NotificationRequestedListener {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${messaging.kafka.topics.notification-requested:notification.requested}",
            groupId = "notification-service",
            concurrency = "3",
            properties = "spring.json.value.default.type=com.insurance.common.notification.NotificationRequest")
    public void onNotificationRequested(@Payload NotificationRequest request,
                                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                        @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("notification.requested {}@{} {} for {} {}", partition, offset, request.eventType(), request.referenceType(), request.referenceNumber());
        List<NotificationResponse> created = notificationService.accept(request);
        log.info("{} notification(s) created for {}", created.size(), request.idempotencyKey());
    }
}
