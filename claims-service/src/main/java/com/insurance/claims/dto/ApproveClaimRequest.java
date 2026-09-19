package com.insurance.claims.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ApproveClaimRequest(@NotNull(message = "approvedAmount is required") @Positive BigDecimal approvedAmount,
                                  @Size(max = 500) String reason) {
}
