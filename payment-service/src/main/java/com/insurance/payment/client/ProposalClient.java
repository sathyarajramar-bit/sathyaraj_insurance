package com.insurance.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "proposal-service", path = "/api/proposals")
public interface ProposalClient {

    @GetMapping("/{proposalNumber}")
    ProposalView get(@PathVariable("proposalNumber") String proposalNumber);

    record ProposalView(String proposalNumber, String status, Long userId, Long customerId, String productCode,
                        BigDecimal premiumAmount) {
    }
}
