package com.insurance.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(@NotBlank(message = "reason is required") @Size(max = 500) String reason) {
}
