package com.insurance.payment.event;

import com.insurance.payment.client.PaymentSuccessfulEvent;
import com.insurance.payment.client.PolicyGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Synchronous HTTP delivery to policy-service (Feign through Eureka, SERVICE token). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "messaging.kafka", name = "enabled", havingValue = "false", matchIfMissing = true)
public class HttpOutboxTransport implements OutboxTransport {

    private final PolicyGateway policyGateway;

    @Override
    public void deliver(PaymentSuccessfulEvent event) {
        policyGateway.issue(event);
    }

    @Override
    public String name() {
        return "policy-service (HTTP)";
    }
}
