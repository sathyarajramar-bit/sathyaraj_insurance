package com.insurance.policy.dto;

import com.insurance.policy.entity.PolicyStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PolicyResponse(Long id, String policyNumber, PolicyStatus status, Long userId, Long customerId,
                             String proposalNumber, String quoteNumber, String paymentReference, Long productId, String productCode,
                             String productName, String coverageType, Integer termMonths, Long vehicleId, String registrationNumber,
                             BigDecimal idv, BigDecimal premiumAmount, String holderName, String holderEmail, String nomineeName,
                             String nomineeRelationship, LocalDate startDate, LocalDate endDate, Instant issuedAt,
                             String renewedFromPolicyNumber, String renewedByPolicyNumber, Instant cancelledAt,
                             String cancellationReason, Long scheduleDocumentId,
                             boolean renewalEligible, String renewalMessage, Instant createdAt) {
}
