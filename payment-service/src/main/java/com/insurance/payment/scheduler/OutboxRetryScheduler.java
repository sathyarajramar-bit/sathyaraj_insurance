package com.insurance.payment.scheduler;

import com.insurance.payment.entity.OutboxEvent;
import com.insurance.payment.entity.OutboxStatus;
import com.insurance.payment.event.OutboxDispatcher;
import com.insurance.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Redelivers PENDING outbox rows whose backoff has elapsed. Idempotent receiver => safe on many instances. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRetryScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxDispatcher dispatcher;

    @Scheduled(cron = "${payment.outbox.cron:0 * * * * *}")
    public int redeliverPending() {
        List<OutboxEvent> due = outboxRepository.findTop50ByStatusAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(OutboxStatus.PENDING, Instant.now());
        due.forEach(dispatcher::deliver);
        if (!due.isEmpty()) {
            log.info("Outbox retry processed {} event(s)", due.size());
        }
        return due.size();
    }
}
