package com.insurance.notification.dto;

import com.insurance.notification.entity.Channel;
import com.insurance.notification.entity.DeliveryStatus;

import java.time.Instant;

public record NotificationResponse(Long id, String eventType, Channel channel, Long userId, String recipient, String referenceType,
                                   String referenceNumber, String subject, String body, DeliveryStatus status, int attempts,
                                   String lastError, Instant sentAt, Instant createdAt) {
}
