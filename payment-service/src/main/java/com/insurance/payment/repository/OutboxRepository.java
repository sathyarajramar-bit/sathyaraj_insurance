package com.insurance.payment.repository;

import com.insurance.payment.entity.OutboxEvent;
import com.insurance.payment.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(OutboxStatus status, Instant before);

    Optional<OutboxEvent> findByPaymentReferenceAndEventType(String paymentReference, String eventType);
}
