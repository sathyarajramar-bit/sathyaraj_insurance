package com.insurance.customer.dto;

import com.insurance.customer.entity.KycStatus;
import jakarta.validation.constraints.NotNull;

/** ADMIN/AGENT decision: VERIFIED or REJECTED. */
public record KycDecisionRequest(@NotNull(message = "Decision is required") KycStatus decision) {
}
