package com.insurance.claims.dto;

import com.insurance.claims.entity.ClaimType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegisterClaimRequest(
        @NotBlank(message = "policyNumber is required") @Size(max = 30) String policyNumber,
        @NotNull(message = "claimType is required") ClaimType claimType,
        @NotNull(message = "incidentDate is required") @PastOrPresent(message = "incidentDate cannot be in the future") LocalDate incidentDate,
        @NotBlank(message = "incidentLocation is required") @Size(max = 255) String incidentLocation,
        @NotBlank(message = "description is required") @Size(min = 20, max = 2000, message = "description must be 20 to 2000 characters") String description,
        @NotNull(message = "claimedAmount is required") @Positive BigDecimal claimedAmount) {
}
