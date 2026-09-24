package com.insurance.payment.event;

import com.insurance.common.messaging.MessagingProperties;
import com.insurance.payment.client.PaymentSuccessfulEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka delivery: one record per successful payment on {@code payment.succeeded}, keyed by paymentReference so
 * every event about one payment lands on the same partition (ordered). The send is awaited: the outbox row is marked
 * DELIVERED only after the broker acknowledged the record (acks=all), otherwise the dispatcher's retry/backoff
 * applies exactly as for an HTTP failure. This is the transactional outbox pattern with Kafka as the transport.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "messaging.kafka", name = "enabled", havingValue = "true")
public class KafkaOutboxTransport implements OutboxTransport {

    static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public KafkaOutboxTransport(KafkaTemplate<String, Object> kafkaTemplate, MessagingProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.getTopics().getPaymentSucceeded();
    }

    @Override
    public void deliver(PaymentSuccessfulEvent event) {
        try {
            RecordMetadata meta = kafkaTemplate.send(topic, event.paymentReference(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS).getRecordMetadata();
            log.info("PaymentSuccessful {} published to {}-{}@{}", event.paymentReference(), meta.topic(), meta.partition(), meta.offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing " + event.paymentReference(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka publish of " + event.paymentReference() + " to " + topic + " failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "Kafka topic " + topic;
    }
}
