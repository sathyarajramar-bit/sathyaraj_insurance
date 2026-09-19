package com.insurance.payment.client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * THE integration event of the platform: a premium was collected. policy-service issues (or renews) a
 * policy from it, idempotently by paymentReference. Serialised into the outbox and sent over HTTP today;
 * the same record would be the Kafka message value.
 */
public record PaymentSuccessfulEvent(String paymentReference, String purpose, String proposalNumber, String policyNumber,
                                     Long userId, Long customerId, BigDecimal amount, String currency, Instant paidAt) {
}
