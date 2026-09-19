package com.insurance.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.payment.client.PaymentSuccessfulEvent;
import com.insurance.payment.entity.OutboxEvent;
import com.insurance.payment.entity.OutboxStatus;
import com.insurance.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Writes the event row inside the caller's transaction (same commit as the payment). */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    public static final String PAYMENT_SUCCESSFUL = "PaymentSuccessful";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEvent write(PaymentSuccessfulEvent event) {
        try {
            return outboxRepository.save(OutboxEvent.builder()
                    .paymentReference(event.paymentReference())
                    .eventType(PAYMENT_SUCCESSFUL)
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .nextAttemptAt(Instant.now())
                    .createdAt(Instant.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise PaymentSuccessfulEvent", e);
        }
    }
}
