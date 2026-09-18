package com.insurance.proposal.dto;

import com.insurance.proposal.entity.ProposalStatus;

import java.time.Instant;

public record StatusHistoryResponse(ProposalStatus fromStatus, ProposalStatus toStatus, String changedBy, String reason,
                                    Instant changedAt) {
}
