package com.insurance.proposal.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Consumers: notification-service (customer confirmation), agent work queue. */
public record ProposalSubmittedEvent(String proposalNumber, String quoteNumber, Long userId, Long customerId,
                                     String productCode, BigDecimal premiumAmount, Instant occurredAt) {
}
