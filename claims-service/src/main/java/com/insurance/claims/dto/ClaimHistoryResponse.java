package com.insurance.claims.dto;

import com.insurance.claims.entity.ClaimStatus;

import java.time.Instant;

public record ClaimHistoryResponse(ClaimStatus fromStatus, ClaimStatus toStatus, String changedBy, String reason, Instant changedAt) {
}
