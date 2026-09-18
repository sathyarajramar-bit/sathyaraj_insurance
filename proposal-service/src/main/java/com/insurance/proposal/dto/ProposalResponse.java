package com.insurance.proposal.dto;

import com.insurance.proposal.entity.ProposalStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ProposalResponse(Long id, String proposalNumber, ProposalStatus status, Long userId, Long customerId,
                               String quoteNumber, Long quoteId, Long productId, String productCode, String productName,
                               String coverageType, Integer termMonths, Long vehicleId, String registrationNumber,
                               BigDecimal idv, BigDecimal premiumAmount, ProposerDto proposer, NomineeDto nominee,
                               boolean declarationsAccepted, Instant submittedAt, String reviewedBy, Instant reviewedAt,
                               String decisionReason, Instant createdAt, Instant updatedAt) {
}
