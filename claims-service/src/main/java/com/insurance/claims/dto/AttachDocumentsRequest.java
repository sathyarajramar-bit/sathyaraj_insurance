package com.insurance.claims.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Ids of documents already uploaded to document-service with referenceType CLAIM and this claim number. */
public record AttachDocumentsRequest(@NotEmpty(message = "documentIds is required") @Size(max = 20) List<Long> documentIds) {
}
