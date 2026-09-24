package com.insurance.common.messaging;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka-native producer interceptor (registered through {@code interceptor.classes}, instantiated by the client,
 * hence the no-arg constructor and no Spring injection): copies the request's correlation id from the MDC into a
 * record header so the consumer's log lines carry the same id as the HTTP request that produced the event.
 */
public class CorrelationIdProducerInterceptor implements ProducerInterceptor<Object, Object> {

    private String source = "unknown";

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        if (record.headers().lastHeader(KafkaHeaderNames.CORRELATION_ID) == null) {
            String correlationId = MDC.get(KafkaHeaderNames.CORRELATION_ID);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            record.headers().add(KafkaHeaderNames.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        if (record.headers().lastHeader(KafkaHeaderNames.SOURCE) == null) {
            record.headers().add(KafkaHeaderNames.SOURCE, source.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // nothing: the sender logs success/failure
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
        Object clientId = configs.get("client.id");
        if (clientId != null && !clientId.toString().isBlank()) {
            source = clientId.toString().replaceFirst("-\\d+$", "");   // Spring numbers producers per thread: "payment-service-1"
        }
    }
}
