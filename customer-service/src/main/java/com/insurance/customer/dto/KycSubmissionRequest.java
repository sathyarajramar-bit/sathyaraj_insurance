package com.insurance.customer.dto;

import com.insurance.customer.entity.KycDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KycSubmissionRequest(
        @NotNull(message = "Document type is required") KycDocumentType documentType,
        @NotBlank(message = "Document number is required") @Size(min = 4, max = 50) String documentNumber) {
}
