package com.insurance.payment.event;

import com.insurance.common.notification.NotificationPublisher;
import com.insurance.payment.client.PolicyClient;
import com.insurance.payment.client.ProposalClient;
import com.insurance.payment.entity.OutboxStatus;
import com.insurance.payment.repository.OutboxRepository;
import com.insurance.payment.support.TestJwt;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With messaging.kafka.enabled=true the outbox publishes PaymentSuccessful to Kafka instead of calling
 * policy-service over HTTP. Runs against an in-process KRaft broker (no Docker needed).
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "messaging.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.client-id=payment-service",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "spring.kafka.producer.properties.spring.json.add.type.headers=false",
        "spring.kafka.producer.properties.interceptor.classes=com.insurance.common.messaging.CorrelationIdProducerInterceptor"
})
@EmbeddedKafka(partitions = 3, topics = "payment.succeeded", kraft = true)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class KafkaOutboxIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private OutboxRepository outboxRepository;
    @Autowired private EmbeddedKafkaBroker broker;
    @Autowired private OutboxTransport transport;
    @MockitoBean private ProposalClient proposalClient;
    @MockitoBean private PolicyClient policyClient;
    @MockitoBean private NotificationPublisher notifications;

    static final String JANE = TestJwt.bearer(42, "jane@x.com", "CUSTOMER");
    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        when(proposalClient.get(anyString())).thenAnswer(inv -> new ProposalClient.ProposalView(inv.getArgument(0), "APPROVED", 42L, 7L,
                "MOTOR-CAR-COMP", new BigDecimal("21730.88")));
        Map<String, Object> props = KafkaTestUtils.consumerProps("kafka-outbox-it-" + UUID.randomUUID(), "false", broker);
        consumer = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer()).createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "payment.succeeded");
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void kafkaTransportIsSelectedWhenMessagingIsEnabled() {
        assertThat(transport).isInstanceOf(KafkaOutboxTransport.class);
    }

    @Test
    void successfulPaymentIsPublishedToKafkaKeyedByPaymentReference_andPolicyServiceIsNotCalledOverHttp() throws Exception {
        String proposal = "PR-KAFKA-" + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"proposalNumber\":\"" + proposal + "\",\"paymentMethod\":\"CARD\",\"instrument\":\"4111********1111\"}";

        String response = mockMvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, JANE)
                        .header("Idempotency-Key", UUID.randomUUID().toString()).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        String reference = com.jayway.jsonpath.JsonPath.read(response, "$.paymentReference");

        ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(consumer, "payment.succeeded", Duration.ofSeconds(15));
        assertThat(record.key()).isEqualTo(reference);
        assertThat(record.value()).contains("\"paymentReference\":\"" + reference + "\"").contains("\"proposalNumber\":\"" + proposal + "\"")
                .contains("\"purpose\":\"NEW_POLICY\"");
        assertThat(record.headers().lastHeader("correlationId")).as("correlation id propagated as a header").isNotNull();
        assertThat(new String(record.headers().lastHeader("source").value(), StandardCharsets.UTF_8)).isEqualTo("payment-service");

        await().untilAsserted(() -> assertThat(outboxRepository.findByPaymentReferenceAndEventType(reference, "PaymentSuccessful"))
                .get().extracting(e -> e.getStatus()).isEqualTo(OutboxStatus.DELIVERED));
        verify(policyClient, never()).issue(any());
    }
}
