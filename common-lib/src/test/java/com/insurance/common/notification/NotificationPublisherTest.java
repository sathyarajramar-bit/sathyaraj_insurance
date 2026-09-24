package com.insurance.common.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationPublisherTest {

    /** In-memory transaction manager: enough for Spring to run synchronizations on commit/rollback. */
    static class NoopTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }

    private final NotificationClient client = mock(NotificationClient.class);
    private final List<Runnable> queued = new ArrayList<>();          // executor that records instead of running
    private final NotificationPublisher publisher = new NotificationPublisher(new FeignNotificationTransport(client), queued::add);
    private final TransactionTemplate tx = new TransactionTemplate(new NoopTransactionManager());
    private final NotificationRequest request = NotificationRequest.of(NotificationEventType.POLICY_CANCELLED, 7L, 3L,
            "jane@example.com", null, "POLICY", "PL-1", Map.of("policyNumber", "PL-1", "reason", "sold the car"));

    @BeforeEach
    void drain() {
        queued.clear();
    }

    @Test
    void outsideATransactionItIsHandedToTheExecutorImmediately() {
        publisher.publish(request);

        assertThat(queued).hasSize(1);
        queued.forEach(Runnable::run);
        verify(client).send(request);
    }

    @Test
    void insideATransactionNothingLeavesUntilCommit() {
        tx.executeWithoutResult(status -> {
            publisher.publish(request);
            assertThat(queued).as("must not dispatch before commit").isEmpty();
        });

        assertThat(queued).hasSize(1);
        queued.forEach(Runnable::run);
        verify(client).send(request);
    }

    @Test
    void aRolledBackTransactionSendsNothing() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            publisher.publish(request);
            throw new IllegalStateException("optimistic lock");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(queued).isEmpty();
        verify(client, never()).send(any());
    }

    @Test
    void deliveryFailureIsLoggedNotThrown() {
        doThrow(new RuntimeException("connect refused")).when(client).send(any());

        publisher.publish(request);
        queued.forEach(Runnable::run);             // would propagate if deliver() did not catch

        verify(client).send(request);
    }

    @Test
    void executorRejectionIsLoggedNotThrown() {
        NotificationPublisher saturated = new NotificationPublisher(new FeignNotificationTransport(client), r -> { throw new java.util.concurrent.RejectedExecutionException("full"); });

        saturated.publish(request);                // no exception reaches the business transaction

        verify(client, never()).send(any());
    }
}
