package com.insurance.proposal.event;

import com.insurance.proposal.entity.ProposalStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** APPROVED: payment-service may collect the premium; REJECTED: notify the customer with the reason. */
public record ProposalDecidedEvent(String proposalNumber, ProposalStatus decision, Long userId, Long customerId,
                                   String productCode, BigDecimal premiumAmount, String reason, Instant occurredAt) {
}
