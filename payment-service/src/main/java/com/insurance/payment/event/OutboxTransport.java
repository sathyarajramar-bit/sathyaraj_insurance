package com.insurance.payment.event;

import com.insurance.payment.client.PaymentSuccessfulEvent;

/**
 * How a PaymentSuccessful outbox row leaves payment-service. {@link HttpOutboxTransport} calls policy-service's
 * {@code POST /api/policies/issue} directly (default); {@link KafkaOutboxTransport} publishes to the
 * {@code payment.succeeded} topic and policy-service consumes it ({@code messaging.kafka.enabled=true}).
 * Either way the outbox row is only marked DELIVERED when the call/send succeeded, so retries stay identical.
 */
public interface OutboxTransport {

    void deliver(PaymentSuccessfulEvent event);

    String name();
}
