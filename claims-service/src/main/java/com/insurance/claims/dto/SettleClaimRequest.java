package com.insurance.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Settlement = the approved amount was paid out; the reference is the bank/UTR number recorded by finance. */
public record SettleClaimRequest(@NotBlank(message = "settlementReference is required") @Size(max = 64) String settlementReference) {
}
