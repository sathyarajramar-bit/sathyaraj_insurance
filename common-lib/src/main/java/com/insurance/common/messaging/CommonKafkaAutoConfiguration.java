package com.insurance.common.messaging;

import com.insurance.common.exception.BusinessException;
import com.insurance.common.notification.CommonNotificationAutoConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.common.notification.NotificationTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Everything Kafka that is the same in every service, active only with {@code messaging.kafka.enabled=true}:
 * <ul>
 *   <li>the platform topics (and their dead-letter twins) declared as {@link NewTopic} beans so
 *       {@code KafkaAdmin} creates them at startup - producers and consumers share one definition;</li>
 *   <li>the consumer error policy: retry with exponential backoff, then park the record on
 *       {@code <topic>.DLT} instead of blocking the partition forever. Business-rule failures
 *       ({@link BusinessException}) are not retried - a proposal that is not APPROVED will not become
 *       APPROVED by retrying - and go to the DLT immediately;</li>
 *   <li>correlation-id propagation into listener MDC;</li>
 *   <li>the Kafka {@link NotificationTransport}, which replaces the Feign one in every producer.</li>
 * </ul>
 * Serialisers, bootstrap servers, acks and consumer settings are ordinary {@code spring.kafka.*} properties in
 * the shared {@code application.yml} served by the config-server.
 */
@Slf4j
@AutoConfiguration(after = KafkaAutoConfiguration.class, before = CommonNotificationAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "messaging.kafka", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MessagingProperties.class)
public class CommonKafkaAutoConfiguration {

    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    NewTopic paymentSucceededTopic(MessagingProperties props) {
        return TopicBuilder.name(props.getTopics().getPaymentSucceeded()).partitions(props.getPartitions()).replicas(1).build();
    }

    @Bean
    NewTopic paymentSucceededDeadLetterTopic(MessagingProperties props) {
        return TopicBuilder.name(props.getTopics().getPaymentSucceeded() + DLT_SUFFIX).partitions(props.getPartitions()).replicas(1).build();
    }

    @Bean
    NewTopic notificationRequestedTopic(MessagingProperties props) {
        return TopicBuilder.name(props.getTopics().getNotificationRequested()).partitions(props.getPartitions()).replicas(1).build();
    }

    @Bean
    NewTopic notificationRequestedDeadLetterTopic(MessagingProperties props) {
        return TopicBuilder.name(props.getTopics().getNotificationRequested() + DLT_SUFFIX).partitions(props.getPartitions()).replicas(1).build();
    }

    /**
     * Record values are written with the application's own ObjectMapper (ISO-8601 dates, same JSON as the REST APIs) instead
     * of spring-kafka's default mapper, which writes Instants as numeric timestamps. Type headers stay off through the
     * {@code spring.json.add.type.headers=false} producer property (a JsonSerializer must not be configured by both
     * setters and properties), so consumers choose their own class.
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    DefaultKafkaProducerFactoryCustomizer platformJsonValueSerializer(ObjectMapper objectMapper) {
        return factory -> ((DefaultKafkaProducerFactory) factory).setValueSerializer(new JsonSerializer<>(objectMapper));
    }

    /** Picked up by Boot's listener-container factory: every @KafkaListener gets the correlation id in its MDC. */
    @Bean
    @ConditionalOnMissingBean(RecordInterceptor.class)
    RecordInterceptor<Object, Object> correlationIdRecordInterceptor() {
        return new CorrelationIdRecordInterceptor();
    }

    /** Picked up by Boot's listener-container factory: retry with backoff, then dead-letter. */
    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    CommonErrorHandler kafkaListenerErrorHandler(KafkaOperations<Object, Object> template, MessagingProperties props) {
        // same partition on "<topic>.DLT" (the NewTopic beans above); spring-kafka's default suffix would be "-dlt"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(props.getConsumer().getMaxRetries());
        backOff.setInitialInterval(props.getConsumer().getInitialBackoffMs());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(props.getConsumer().getMaxBackoffMs());
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(BusinessException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Kafka record {}-{}@{} failed attempt {}: {}", record.topic(), record.partition(), record.offset(), attempt, ex.toString()));
        return handler;
    }

    @Bean
    @ConditionalOnProperty(prefix = "notifications", name = "enabled", havingValue = "true", matchIfMissing = true)
    NotificationTransport kafkaNotificationTransport(KafkaTemplate<String, Object> kafkaTemplate, MessagingProperties props) {
        log.info("Notifications will be published to Kafka topic {}", props.getTopics().getNotificationRequested());
        return new KafkaNotificationTransport(kafkaTemplate, props.getTopics().getNotificationRequested(), 10);
    }
}
