package com.insurance.policy.messaging;

import com.insurance.policy.dto.IssuePolicyRequest;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.service.PolicyIssuanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer of {@code payment.succeeded} (group {@code policy-service}). The payload is payment-service's
 * PaymentSuccessfulEvent, deserialised into {@link IssuePolicyRequest} - the same contract the HTTP endpoint
 * {@code POST /api/policies/issue} accepts, so both paths call the same idempotent
 * {@link PolicyIssuanceService#issue} and a redelivered record (at-least-once) returns the existing policy.
 *
 * <p>Errors: the container's DefaultErrorHandler (common-lib) retries transient failures with backoff and parks
 * the record on {@code payment.succeeded.DLT}; a PolicyException (business rule) goes to the DLT immediately.
 * The offset is committed only after this method returns normally (ack-mode RECORD).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging.kafka", name = "enabled", havingValue = "true")
public class PaymentSucceededListener {

    private final PolicyIssuanceService issuanceService;

    @KafkaListener(
            topics = "${messaging.kafka.topics.payment-succeeded:payment.succeeded}",
            groupId = "policy-service",
            properties = "spring.json.value.default.type=com.insurance.policy.dto.IssuePolicyRequest")
    public void onPaymentSucceeded(@Payload IssuePolicyRequest event,
                                   @Header(KafkaHeaders.RECEIVED_KEY) String key,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                   @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("payment.succeeded {}@{} key={} purpose={} proposal={}", partition, offset, key, event.purpose(), event.proposalNumber());
        Policy policy = issuanceService.issue(event);
        log.info("Policy {} {} for payment {}", policy.getPolicyNumber(), policy.getStatus(), event.paymentReference());
    }
}
