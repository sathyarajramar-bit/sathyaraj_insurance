package com.insurance.notification.messaging;

import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.notification.client.CustomerClient;
import com.insurance.notification.entity.Channel;
import com.insurance.notification.entity.DeliveryStatus;
import com.insurance.notification.entity.Notification;
import com.insurance.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * notification-service as a Kafka consumer: a NotificationRequest on notification.requested (published by any service's
 * NotificationPublisher over the Kafka transport) is stored and delivered exactly like a POST /api/notifications, and a
 * redelivered record is ignored by the idempotency key.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "messaging.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.client-id=notification-service",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.key.delegate.class=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=com.insurance.*",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false",
        "spring.kafka.listener.ack-mode=record"
})
@EmbeddedKafka(partitions = 3, topics = {"notification.requested", "notification.requested.DLT"}, kraft = true)
@ActiveProfiles("test")
class NotificationRequestedListenerIT {

    /** The same producer-side bean every other service has (KafkaNotificationTransport under the hood). */
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private NotificationRepository repository;
    @MockitoBean private CustomerClient customerClient;

    @BeforeEach
    void stubs() {
        when(customerClient.getById(7L)).thenReturn(new CustomerClient.CustomerContact(7L, 42L, "jane@x.com", "9876543210", "Jane"));
    }

    @Test
    void aRecordOnNotificationRequestedIsStoredAndSent_andRedeliveryIsIgnored() throws Exception {
        String policy = "PL-K-" + UUID.randomUUID().toString().substring(0, 8);
        NotificationRequest request = NotificationRequest.of(NotificationEventType.POLICY_ISSUED, 42L, 7L, null, null, "POLICY", policy,   // no contact in the event: resolved from customer-service
                Map.of("policyNumber", policy, "product", "Comprehensive Car", "startDate", "2026-09-22", "endDate", "2027-09-21", "premium", "21730.88"));

        kafkaTemplate.send("notification.requested", request.idempotencyKey(), request).get();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<Notification> rows = repository.findByReferenceTypeAndReferenceNumber("POLICY", policy, Pageable.unpaged()).getContent();
            assertThat(rows).extracting(Notification::getChannel).containsExactlyInAnyOrder(Channel.EMAIL, Channel.SMS);
            assertThat(rows).allSatisfy(n -> {
                assertThat(n.getStatus()).isEqualTo(DeliveryStatus.SENT);
                assertThat(n.getIdempotencyKey()).isEqualTo("POLICY_ISSUED:" + policy);
            });
            assertThat(rows.stream().filter(n -> n.getChannel() == Channel.EMAIL).findFirst().orElseThrow().getRecipient()).isEqualTo("jane@x.com");
        });

        kafkaTemplate.send("notification.requested", request.idempotencyKey(), request).get();    // at-least-once redelivery
        Thread.sleep(1500);
        assertThat(repository.findByReferenceTypeAndReferenceNumber("POLICY", policy, Pageable.unpaged()).getContent())
                .as("the idempotency key stops a second e-mail/SMS").hasSize(2);
    }
}
