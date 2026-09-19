package com.insurance.document.dto;

import com.insurance.document.entity.DocumentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Reviewer decision: VERIFIED or REJECTED (with reason). */
public record DocumentStatusRequest(@NotNull DocumentStatus status, @Size(max = 255) String reason) {
}
