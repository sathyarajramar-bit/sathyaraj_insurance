package com.insurance.policy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelPolicyRequest(@NotBlank(message = "reason is required") @Size(max = 500) String reason) {
}
