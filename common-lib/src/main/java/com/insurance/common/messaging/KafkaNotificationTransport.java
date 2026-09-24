package com.insurance.common.messaging;

import com.insurance.common.notification.NotificationRequest;
import com.insurance.common.notification.NotificationTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.TimeUnit;

/**
 * Publishes a {@link NotificationRequest} to the {@code notification.requested} topic instead of calling
 * notification-service over HTTP. The record key is the idempotency key (e.g. "POLICY_ISSUED:PL-1"), so a redelivery
 * of the same event lands on the same partition and notification-service's unique (idempotency_key, channel)
 * constraint makes it a no-op.
 *
 * <p>The send is awaited so that a broker outage surfaces as an exception in the publisher's log line rather
 * than silently disappearing in the producer's buffer.
 */
@Slf4j
public class KafkaNotificationTransport implements NotificationTransport {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final long sendTimeoutSeconds;

    public KafkaNotificationTransport(KafkaTemplate<String, Object> kafkaTemplate, String topic, long sendTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Override
    public void send(NotificationRequest request) {
        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, request.idempotencyKey(), request).get(sendTimeoutSeconds, TimeUnit.SECONDS);
            RecordMetadata meta = result.getRecordMetadata();
            log.debug("Notification {} published to {}-{}@{}", request.idempotencyKey(), meta.topic(), meta.partition(), meta.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + request.idempotencyKey(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka publish of " + request.idempotencyKey() + " to " + topic + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "kafka";
    }
}
