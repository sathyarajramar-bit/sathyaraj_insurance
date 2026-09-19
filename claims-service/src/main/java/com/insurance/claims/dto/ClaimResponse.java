package com.insurance.claims.dto;

import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.entity.ClaimType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ClaimResponse(Long id, String claimNumber, ClaimStatus status, Long userId, Long customerId, String policyNumber,
                            String productCode, String registrationNumber, BigDecimal idv, ClaimType claimType, LocalDate incidentDate,
                            String incidentLocation, String description, BigDecimal claimedAmount, BigDecimal assessedAmount,
                            BigDecimal approvedAmount, BigDecimal settledAmount, String settlementReference, String assessor,
                            String assessmentNotes, String decisionReason, String documentsRequested, Instant documentsRequestedAt,
                            Instant settledAt, Instant closedAt, int reopenedCount, List<ClaimDocumentResponse> documents,
                            Instant createdAt, Instant updatedAt) {
}
