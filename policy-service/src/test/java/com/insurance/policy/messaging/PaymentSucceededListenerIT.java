package com.insurance.policy.messaging;

import com.insurance.common.notification.NotificationPublisher;
import com.insurance.policy.client.DocumentClient;
import com.insurance.policy.client.PaymentClient;
import com.insurance.policy.client.ProposalClient;
import com.insurance.policy.entity.PolicyStatus;
import com.insurance.policy.repository.PolicyRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * policy-service as a Kafka consumer: a PaymentSuccessful record on payment.succeeded issues the policy through the
 * same idempotent service as the HTTP endpoint; a business-rule failure is not retried and lands on the DLT.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "messaging.kafka.enabled=true",
        "messaging.kafka.consumer.max-retries=2",
        "messaging.kafka.consumer.initial-backoff-ms=50",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.client-id=policy-service",
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
@EmbeddedKafka(partitions = 3, topics = {"payment.succeeded", "payment.succeeded.DLT"}, kraft = true)
@ActiveProfiles("test")
class PaymentSucceededListenerIT {

    private Producer<String, String> producer;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private EmbeddedKafkaBroker broker;
    @MockitoBean private ProposalClient proposalClient;
    @MockitoBean private PaymentClient paymentClient;
    @MockitoBean private DocumentClient documentClient;
    @MockitoBean private NotificationPublisher notifications;

    @BeforeEach
    void stubs() {
        // plain String producer, like payment-service: the JSON is exactly what its JsonSerializer emits
        producer = new DefaultKafkaProducerFactory<>(KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()).createProducer();
        when(proposalClient.get(anyString())).thenAnswer(inv -> new ProposalClient.ProposalView(inv.getArgument(0), "APPROVED", 42L, 7L, "QT-1", 1L,
                "MOTOR-CAR-COMP", "Comprehensive Car", "COMPREHENSIVE", 12, 5L, "MH12AB1234", new BigDecimal("600000"), new BigDecimal("21730.88"),
                new ProposalClient.Proposer("Jane", "Doe", "jane@x.com"), new ProposalClient.Nominee("John", "SPOUSE")));
        when(documentClient.storeGenerated(any())).thenReturn(new DocumentClient.DocumentView(77L, "x", "UPLOADED"));
    }

    private static String event(String paymentRef, String proposal) {
        return "{\"paymentReference\":\"" + paymentRef + "\",\"purpose\":\"NEW_POLICY\",\"proposalNumber\":\"" + proposal
                + "\",\"policyNumber\":null,\"userId\":42,\"customerId\":7,\"amount\":21730.88,\"currency\":\"INR\",\"paidAt\":\"2026-09-22T10:00:00Z\"}";
    }

    @org.junit.jupiter.api.AfterEach
    void closeProducer() {
        producer.close();
    }

    @Test
    void aPaymentSucceededRecordIssuesThePolicy_andARedeliveryIsIdempotent() throws Exception {
        String pay = "PAY-K-" + UUID.randomUUID().toString().substring(0, 8);
        String proposal = "PR-K-" + UUID.randomUUID().toString().substring(0, 8);
        when(paymentClient.get(pay)).thenReturn(new PaymentClient.PaymentView(pay, "SUCCESS", "NEW_POLICY", proposal, null, 42L, 7L, new BigDecimal("21730.88")));

        producer.send(new ProducerRecord<>("payment.succeeded", pay, event(pay, proposal))).get();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(policyRepository.findByPaymentReference(pay)).get().extracting(p -> p.getStatus()).isEqualTo(PolicyStatus.ACTIVE));

        producer.send(new ProducerRecord<>("payment.succeeded", pay, event(pay, proposal))).get();      // at-least-once: same record again
        Thread.sleep(1500);
        assertThat(policyRepository.findAll().stream().filter(p -> pay.equals(p.getPaymentReference())).count())
                .as("one policy per payment reference, however often the record is delivered").isEqualTo(1);
    }

    @Test
    void aBusinessRuleFailureGoesStraightToTheDeadLetterTopic() throws Exception {
        String pay = "PAY-F-" + UUID.randomUUID().toString().substring(0, 8);
        when(paymentClient.get(pay)).thenReturn(new PaymentClient.PaymentView(pay, "FAILED", "NEW_POLICY", "PR-F", null, 42L, 7L, BigDecimal.TEN));

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-it-" + UUID.randomUUID(), "false", broker);
        try (Consumer<String, String> dlt = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(dlt, "payment.succeeded.DLT");

            producer.send(new ProducerRecord<>("payment.succeeded", pay, event(pay, "PR-F"))).get();

            ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(dlt, "payment.succeeded.DLT", Duration.ofSeconds(20));
            assertThat(dead.key()).isEqualTo(pay);
            assertThat(new String(dead.headers().lastHeader("kafka_dlt-exception-message").value()))
                    .contains("a policy requires a SUCCESS payment");
        }
        assertThat(policyRepository.findByPaymentReference(pay)).isEmpty();
    }
}
