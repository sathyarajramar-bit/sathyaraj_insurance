package com.insurance.claims.dto;

import java.time.Instant;

public record ClaimDocumentResponse(Long documentId, String documentType, String fileName, String attachedBy, Instant attachedAt) {
}
