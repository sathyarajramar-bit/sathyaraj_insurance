package com.insurance.common.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Consumer-side twin of {@link CorrelationIdProducerInterceptor}: before a listener method runs, the record's
 * correlation id header goes into the MDC (so the shared log pattern prints it), and it is cleared afterwards
 * because listener threads are pooled.
 */
public class CorrelationIdRecordInterceptor implements RecordInterceptor<Object, Object> {

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        Header header = record.headers().lastHeader(KafkaHeaderNames.CORRELATION_ID);
        String correlationId = header == null ? UUID.randomUUID().toString() : new String(header.value(), StandardCharsets.UTF_8);
        MDC.put(KafkaHeaderNames.CORRELATION_ID, correlationId);
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        MDC.remove(KafkaHeaderNames.CORRELATION_ID);
    }
}
