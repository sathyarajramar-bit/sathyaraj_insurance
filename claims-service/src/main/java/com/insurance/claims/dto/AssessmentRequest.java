package com.insurance.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Handler's assessment while UNDER_REVIEW: an assessed amount and notes (both optional until approval). */
public record AssessmentRequest(@PositiveOrZero BigDecimal assessedAmount,
                                @NotBlank(message = "notes are required") @Size(max = 2000) String notes) {
}
