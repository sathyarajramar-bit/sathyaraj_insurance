package com.insurance.document.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.document.dto.DocumentResponse;
import com.insurance.document.dto.DocumentStatusRequest;
import com.insurance.document.dto.GeneratedDocumentRequest;
import com.insurance.document.entity.DocumentType;
import com.insurance.document.entity.ReferenceType;
import com.insurance.document.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Documents", description = "Uploads, generated documents, metadata and downloads")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file for a reference (e.g. CLAIM CL-123, CLAIM_PHOTO)")
    public ResponseEntity<DocumentResponse> upload(@RequestPart("file") MultipartFile file,
                                                   @RequestParam ReferenceType referenceType,
                                                   @RequestParam String referenceNumber,
                                                   @RequestParam DocumentType documentType) throws IOException {
        DocumentResponse response = documentService.upload(referenceType, referenceNumber, documentType,
                file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/generated")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Internal: store a system generated document (JSON + base64)")
    public ResponseEntity<DocumentResponse> generated(@Valid @RequestBody GeneratedDocumentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentService.storeGenerated(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Metadata: owner, ADMIN, AGENT, CLAIMS_HANDLER or SERVICE")
    public DocumentResponse get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download the bytes")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        DocumentService.Download d = documentService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .contentLength(d.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.fileName() + "\"")
                .body(new InputStreamResource(d.content()));
    }

    @GetMapping
    @Operation(summary = "Documents of a reference (?referenceType=CLAIM&referenceNumber=CL-1; customers see own uploads only) or, without params, my documents")
    public PageResponse<DocumentResponse> list(@RequestParam(required = false) ReferenceType referenceType,
                                               @RequestParam(required = false) String referenceNumber,
                                               @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (referenceType != null && referenceNumber != null) {
            return documentService.listByReference(referenceType, referenceNumber, pageable);
        }
        return documentService.listMine(pageable);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','CLAIMS_HANDLER')")
    @Operation(summary = "Verify or reject an uploaded document")
    public DocumentResponse updateStatus(@PathVariable Long id, @Valid @RequestBody DocumentStatusRequest request) {
        return documentService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft delete (owner or ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
