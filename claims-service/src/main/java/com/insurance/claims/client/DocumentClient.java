package com.insurance.claims.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** document-service metadata: used to verify that an attached document exists and belongs to the claimant. */
@FeignClient(name = "document-service", path = "/api/documents")
public interface DocumentClient {

    @GetMapping("/{id}")
    DocumentView get(@PathVariable("id") Long id);

    record DocumentView(Long id, Long ownerUserId, String referenceType, String referenceNumber, String documentType,
                        String fileName, String status) {
    }
}
