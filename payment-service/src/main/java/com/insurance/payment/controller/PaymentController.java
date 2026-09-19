package com.insurance.payment.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.payment.dto.PaymentRequest;
import com.insurance.payment.dto.PaymentResponse;
import com.insurance.payment.dto.RefundRequest;
import com.insurance.payment.entity.PaymentStatus;
import com.insurance.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payments", description = "Premium collection with idempotency and refunds")
public class PaymentController {

    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Pay for an APPROVED proposal or renew a policy. Requires an Idempotency-Key header; a retry with the same key returns the original result.")
    public ResponseEntity<PaymentResponse> pay(
            @Parameter(description = "Client generated unique key (UUID) per payment attempt")
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank @Size(min = 8, max = 100) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.pay(idempotencyKey, request);
        HttpStatus status = response.status() == PaymentStatus.SUCCESS ? HttpStatus.CREATED : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{reference}")
    @Operation(summary = "Payment by reference: owner, ADMIN, AGENT or SERVICE")
    public PaymentResponse get(@PathVariable String reference) {
        return paymentService.getByReference(reference);
    }

    @GetMapping
    @Operation(summary = "My payments, or all with filters (ADMIN/AGENT): ?proposalNumber=&status=&page=&size=&sort=")
    public PageResponse<PaymentResponse> search(
            @RequestParam(required = false) String proposalNumber,
            @RequestParam(required = false) PaymentStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return paymentService.search(proposalNumber, status, pageable);
    }

    @PostMapping("/{reference}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Refund a SUCCESS payment (e.g. policy cancelled in the free-look period)")
    public PaymentResponse refund(@PathVariable String reference, @Valid @RequestBody RefundRequest request) {
        return paymentService.refund(reference, request.reason());
    }
}
