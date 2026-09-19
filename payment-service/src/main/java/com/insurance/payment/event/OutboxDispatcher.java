package com.insurance.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.payment.client.PaymentSuccessfulEvent;
import com.insurance.payment.client.PolicyGateway;
import com.insurance.payment.config.PaymentProperties;
import com.insurance.payment.entity.OutboxEvent;
import com.insurance.payment.entity.OutboxStatus;
import com.insurance.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Delivers outbox events to policy-service.
 *
 * <p>Two triggers share one method: (1) right after the payment transaction commits (fast path, async),
 * (2) the {@code OutboxRetryScheduler} for rows still PENDING (policy-service was down, process crashed
 * between commit and delivery, ...). Delivery is idempotent on the receiver (by paymentReference), so
 * double delivery is harmless and at-least-once is the guarantee.
 *
 * <p>This is the outbox pattern: the same design with Kafka replaces {@link PolicyGateway#issue} by a
 * producer send, and policy-service becomes a consumer of the topic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final OutboxRepository outboxRepository;
    private final PolicyGateway policyGateway;
    private final ObjectMapper objectMapper;
    private final PaymentProperties properties;

    @Async
    @TransactionalEventListener
    public void onCommitted(OutboxCommittedEvent committed) {
        outboxRepository.findById(committed.outboxId()).ifPresent(this::deliver);
    }

    @Transactional
    public void deliver(OutboxEvent event) {
        if (event.getStatus() == OutboxStatus.DELIVERED) {
            return;
        }
        try {
            PaymentSuccessfulEvent payload = objectMapper.readValue(event.getPayload(), PaymentSuccessfulEvent.class);
            policyGateway.issue(payload);
            event.setStatus(OutboxStatus.DELIVERED);
            event.setDeliveredAt(Instant.now());
            event.setLastError(null);
            log.info("Outbox {} {} delivered to policy-service", event.getEventType(), event.getPaymentReference());
        } catch (Exception e) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setLastError(truncate(e.toString()));
            if (attempts >= properties.getOutbox().getMaxAttempts()) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("Outbox {} {} FAILED after {} attempts: {}", event.getEventType(), event.getPaymentReference(), attempts, e.toString());
            } else {
                long delay = Math.min(3600, properties.getOutbox().getBackoffSeconds() * (1L << Math.min(attempts - 1, 7)));
                event.setNextAttemptAt(Instant.now().plusSeconds(delay));
                log.warn("Outbox {} {} delivery attempt {} failed, retry in {}s: {}", event.getEventType(),
                        event.getPaymentReference(), attempts, delay, e.toString());
            }
        }
        outboxRepository.save(event);
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /** In-process signal carrying the outbox row id; published inside the payment transaction. */
    public record OutboxCommittedEvent(Long outboxId) {
    }
}
