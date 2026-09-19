package com.insurance.document.dto;

import com.insurance.document.entity.DocumentStatus;
import com.insurance.document.entity.DocumentType;
import com.insurance.document.entity.ReferenceType;

import java.time.Instant;

public record DocumentResponse(Long id, Long ownerUserId, Long customerId, ReferenceType referenceType, String referenceNumber,
                               DocumentType documentType, String fileName, String contentType, long sizeBytes, String checksumSha256,
                               DocumentStatus status, String statusReason, Instant createdAt) {
}
