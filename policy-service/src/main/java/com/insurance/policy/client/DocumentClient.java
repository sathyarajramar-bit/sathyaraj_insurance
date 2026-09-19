package com.insurance.policy.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Stores the generated policy schedule in document-service (JSON + base64 so no multipart encoder is needed). */
@FeignClient(name = "document-service", path = "/api/documents")
public interface DocumentClient {

    @PostMapping("/generated")
    DocumentView storeGenerated(@RequestBody GeneratedDocumentRequest request);

    record GeneratedDocumentRequest(Long userId, Long customerId, String referenceType, String referenceNumber,
                                    String documentType, String fileName, String contentType, String contentBase64) {
    }

    record DocumentView(Long id, String fileName, String status) {
    }
}
