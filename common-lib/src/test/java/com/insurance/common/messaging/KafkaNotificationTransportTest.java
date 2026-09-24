package com.insurance.common.messaging;

import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationRequest;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaNotificationTransportTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
    private final KafkaNotificationTransport transport = new KafkaNotificationTransport(template, "notification.requested", 1);
    private final NotificationRequest request = NotificationRequest.of(NotificationEventType.POLICY_ISSUED, 7L, 3L, "jane@x.com", null,
            "POLICY", "PL-1", Map.of("policyNumber", "PL-1"));

    @Test
    void publishesWithTheIdempotencyKeyAsRecordKey() {
        ProducerRecord<String, Object> record = new ProducerRecord<>("notification.requested", "POLICY_ISSUED:PL-1", request);
        RecordMetadata meta = new RecordMetadata(new TopicPartition("notification.requested", 1), 5, 0, 0, 0, 0);
        when(template.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(new SendResult<>(record, meta)));

        transport.send(request);

        verify(template).send(eq("notification.requested"), eq("POLICY_ISSUED:PL-1"), eq(request));
    }

    @Test
    void aBrokerFailureSurfacesAsAnExceptionForThePublisherToLog() {
        when(template.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThatThrownBy(() -> transport.send(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("POLICY_ISSUED:PL-1")
                .hasMessageContaining("broker down");
    }
}
