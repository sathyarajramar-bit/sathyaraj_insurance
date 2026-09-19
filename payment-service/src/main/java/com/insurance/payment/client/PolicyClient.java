package com.insurance.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;

/** policy-service: policy for renewal pricing, and the internal idempotent "issue" endpoint fed by the outbox dispatcher. */
@FeignClient(name = "policy-service", path = "/api/policies")
public interface PolicyClient {

    @GetMapping("/{policyNumber}")
    PolicyView get(@PathVariable("policyNumber") String policyNumber);

    @PostMapping("/issue")
    PolicyView issue(@RequestBody PaymentSuccessfulEvent event);

    record PolicyView(String policyNumber, String status, Long userId, Long customerId, BigDecimal premiumAmount,
                      boolean renewalEligible, String renewalMessage) {
    }
}
