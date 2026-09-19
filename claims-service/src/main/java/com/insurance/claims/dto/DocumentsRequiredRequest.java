package com.insurance.claims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DocumentsRequiredRequest(@NotBlank(message = "Describe the documents required") @Size(max = 500) String documentsRequested) {
}
