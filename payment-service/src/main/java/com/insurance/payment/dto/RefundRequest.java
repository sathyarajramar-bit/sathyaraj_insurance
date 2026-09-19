package com.insurance.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequest(@NotBlank(message = "reason is required") @Size(max = 255) String reason) {
}
