package com.insurance.common.messaging;

/** Header names on every platform Kafka record. */
public final class KafkaHeaderNames {

    /** Same value as the HTTP X-Correlation-Id / MDC key, so one id follows a request through HTTP and Kafka hops. */
    public static final String CORRELATION_ID = "correlationId";
    /** Logical producer, e.g. "payment-service". */
    public static final String SOURCE = "source";

    private KafkaHeaderNames() {
    }
}
