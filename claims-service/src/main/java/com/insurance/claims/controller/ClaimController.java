package com.insurance.claims.controller;

import com.insurance.claims.dto.ApproveClaimRequest;
import com.insurance.claims.dto.AssessmentRequest;
import com.insurance.claims.dto.AttachDocumentsRequest;
import com.insurance.claims.dto.ClaimHistoryResponse;
import com.insurance.claims.dto.ClaimResponse;
import com.insurance.claims.dto.DocumentsRequiredRequest;
import com.insurance.claims.dto.ReasonRequest;
import com.insurance.claims.dto.RegisterClaimRequest;
import com.insurance.claims.dto.SettleClaimRequest;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.entity.ClaimType;
import com.insurance.claims.service.ClaimHandlingService;
import com.insurance.claims.service.ClaimService;
import com.insurance.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/claims")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Claims", description = "Claim registration, assessment, decisions and settlement")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimHandlingService handling;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Register a claim on an ACTIVE policy that covers the incident date")
    public ResponseEntity<ClaimResponse> register(@Valid @RequestBody RegisterClaimRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(claimService.register(request));
    }

    @GetMapping("/{claimNumber}")
    @Operation(summary = "Claim by number: claimant, CLAIMS_HANDLER, ADMIN, AGENT or SERVICE")
    public ClaimResponse get(@PathVariable String claimNumber) {
        return claimService.getByNumber(claimNumber);
    }

    @GetMapping("/{claimNumber}/history")
    public List<ClaimHistoryResponse> history(@PathVariable String claimNumber) {
        return claimService.history(claimNumber);
    }

    @GetMapping
    @Operation(summary = "My claims, or search (CLAIMS_HANDLER/ADMIN/AGENT): ?customerId=&policyNumber=&status=&type=&incidentFrom=&incidentTo=&page=&size=&sort=")
    public PageResponse<ClaimResponse> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String policyNumber,
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) ClaimType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return claimService.search(customerId, policyNumber, status, type, incidentFrom, incidentTo, pageable);
    }

    @PutMapping("/{claimNumber}/documents")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN','CLAIMS_HANDLER')")
    @Operation(summary = "Attach documents already uploaded to document-service for this claim")
    public ClaimResponse attachDocuments(@PathVariable String claimNumber, @Valid @RequestBody AttachDocumentsRequest request) {
        return claimService.attachDocuments(claimNumber, request);
    }

    @PutMapping("/{claimNumber}/review")
    @PreAuthorize("hasAnyRole('CLAIMS_HANDLER','ADMIN')")
    public ClaimResponse review(@PathVariable String claimNumber) {
        return handling.startReview(claimNumber);
    }

    @PutMapping("/{claimNumber}/assessment")
    @PreAuthorize("hasAnyRole('CLAIMS_HANDLER','ADMIN')")
    @Operation(summary = "Record the surveyor's assessment while UNDER_REVIEW")
    public ClaimResponse assess(@PathVariable String claimNumber, @Valid @RequestBody AssessmentRequest request) {
        return handling.assess(claimNumber, request);
    }

    @PutMapping("/{claimNumber}/documents-required")
    @PreAuthorize("hasAnyRole('CLAIMS_HANDLER','ADMIN')")
    public ClaimResponse documentsRequired(@PathVariable String claimNumber, @Valid @RequestBody DocumentsRequiredRequest request) {
        return handling.requireDocuments(claimNumber, request.documentsRequested());
    }

    @PutMapping("/{claimNumber}/approve")
    @PreAuthorize("hasAnyRole('CLAIMS_HANDLER','ADMIN')")
    public ClaimResponse approve(@PathVariable String claimNumber, @Valid @RequestBody ApproveClaimRequest request) {
        return handling.approve(claimNumber, request);
    }

    @PutMapping("/{claimNumber}/reject")
    @PreAuthorize("hasAnyRole('CLAIMS_HANDLER','ADMIN')")
    public ClaimResponse reject(@PathVariable String claimNumber, @Valid @RequestBody ReasonRequest request) {
        return handling.reject(claimNumber, request.reason());
    }

    @PutMapping("/{claimNumber}/settle")
    @PreAuthorize("hasAnyRole('CLAIMS_HANDLER','ADMIN')")
    @Operation(summary = "Record the payout of an APPROVED claim")
    public ClaimResponse settle(@PathVariable String claimNumber, @Valid @RequestBody SettleClaimRequest request) {
        return handling.settle(claimNumber, request);
    }

    @PutMapping("/{claimNumber}/close")
    @PreAuthorize("hasAnyRole('CLAIMS_HANDLER','ADMIN')")
    public ClaimResponse close(@PathVariable String claimNumber, @Valid @RequestBody ReasonRequest request) {
        return handling.close(claimNumber, request.reason());
    }

    @PutMapping("/{claimNumber}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Explicit reopen of a CLOSED claim (ADMIN, reason mandatory)")
    public ClaimResponse reopen(@PathVariable String claimNumber, @Valid @RequestBody ReasonRequest request) {
        return handling.reopen(claimNumber, request.reason());
    }
}
