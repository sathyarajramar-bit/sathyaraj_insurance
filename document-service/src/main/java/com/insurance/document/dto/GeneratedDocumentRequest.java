package com.insurance.document.dto;

import com.insurance.document.entity.DocumentType;
import com.insurance.document.entity.ReferenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** System generated documents (policy schedule) sent by other services as JSON + base64: no multipart needed service-to-service. */
public record GeneratedDocumentRequest(
        @NotNull Long userId,
        Long customerId,
        @NotNull ReferenceType referenceType,
        @NotBlank @Size(max = 40) String referenceNumber,
        @NotNull DocumentType documentType,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 100) String contentType,
        @NotBlank String contentBase64) {
}
