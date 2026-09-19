package com.insurance.claims.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;

/** policy-service coverage check: returns the policy if it is in force on the incident date, 422 otherwise. */
@FeignClient(name = "policy-service", path = "/api/policies")
public interface PolicyClient {

    @GetMapping("/{policyNumber}/coverage-check")
    PolicyView coverageCheck(@PathVariable("policyNumber") String policyNumber, @RequestParam("incidentDate") LocalDate incidentDate);

    record PolicyView(String policyNumber, String status, Long userId, Long customerId, String productCode, String coverageType,
                      String registrationNumber, BigDecimal idv, LocalDate startDate, LocalDate endDate) {
    }
}
