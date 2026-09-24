package com.insurance.common.notification;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Fire-and-forget delivery to notification-service, off the request thread and only after the business
 * transaction has committed.
 *
 * <p>Design: a notification is never part of a business transaction's contract. If notification-service
 * is down the customer still gets their quote/policy; the failure is logged (and notification-service's
 * own retry job handles failures on its side once the message arrived). Producers call {@link #publish}
 * from inside their {@code @Transactional} methods: when a transaction is active the request is parked and
 * handed to the executor in {@code afterCommit}, so a rollback (409 optimistic lock, constraint violation)
 * never produces a "policy cancelled" e-mail for a change that did not happen. Outside a transaction it is
 * dispatched immediately. The transport is pluggable: HTTP (Feign) by default, a Kafka producer when
 * {@code messaging.kafka.enabled=true}; same {@link NotificationRequest} payload either way.
 */
@Slf4j
public class NotificationPublisher {

    private final NotificationTransport transport;
    private final Executor executor;

    public NotificationPublisher(NotificationTransport transport, Executor executor) {
        this.transport = transport;
        this.executor = executor;
    }

    public void publish(NotificationRequest request) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();          // correlation id of the request thread
        if (TransactionSynchronizationManager.isActualTransactionActive() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(request, mdc);
                }
            });
        } else {
            dispatch(request, mdc);
        }
    }

    /** The executor thread has no MDC of its own: restore the caller's so logs and Kafka headers keep the correlation id. */
    private void dispatch(NotificationRequest request, Map<String, String> mdc) {
        try {
            executor.execute(() -> {
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                }
                try {
                    deliver(request);
                } finally {
                    MDC.clear();
                }
            });
        } catch (RuntimeException e) {           // executor saturated / shut down: same contract as a failed send
            log.error("Notification {} for {} {} could not be scheduled: {}", request.eventType(), request.referenceType(),
                    request.referenceNumber(), e.toString());
        }
    }

    void deliver(NotificationRequest request) {
        try {
            transport.send(request);
            log.info("Notification {} sent via {} for {} {}", request.eventType(), transport.name(), request.referenceType(), request.referenceNumber());
        } catch (Exception e) {
            log.error("Notification {} for {} {} could not be delivered: {}", request.eventType(), request.referenceType(),
                    request.referenceNumber(), e.toString());
        }
    }
}
