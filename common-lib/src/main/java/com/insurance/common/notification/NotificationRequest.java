package com.insurance.common.notification;

import java.util.Map;

/**
 * The message contract between every producer and notification-service. Carries ids and template
 * parameters only; notification-service resolves channel, template and recipient details.
 *
 * @param idempotencyKey producer-side unique key (e.g. "POLICY_ISSUED:PL-123"); a redelivery is ignored
 * @param params         template variables, e.g. quoteNumber, finalPremium, validUntil
 */
public record NotificationRequest(NotificationEventType eventType, Long userId, Long customerId, String recipientEmail,
                                  String recipientPhone, String referenceType, String referenceNumber,
                                  Map<String, String> params, String idempotencyKey) {

    public static NotificationRequest of(NotificationEventType type, Long userId, Long customerId, String email, String phone,
                                         String referenceType, String referenceNumber, Map<String, String> params) {
        return new NotificationRequest(type, userId, customerId, email, phone, referenceType, referenceNumber, params,
                type + ":" + referenceNumber);
    }
}
