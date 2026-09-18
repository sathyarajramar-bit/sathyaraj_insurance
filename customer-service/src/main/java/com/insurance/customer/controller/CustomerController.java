package com.insurance.customer.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.customer.dto.CreateCustomerRequest;
import com.insurance.customer.dto.CustomerResponse;
import com.insurance.customer.dto.KycDecisionRequest;
import com.insurance.customer.dto.KycSubmissionRequest;
import com.insurance.customer.dto.UpdateCustomerRequest;
import com.insurance.customer.entity.KycStatus;
import com.insurance.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customers", description = "Profiles and KYC")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Create a profile for a newly registered user (called by auth-service, idempotent per userId)")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "My profile")
    public CustomerResponse me() {
        return customerService.getMine();
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update my profile (name, phone, date of birth, gender, address)")
    public CustomerResponse updateMe(@Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.updateMine(request);
    }

    @PutMapping("/me/kyc")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Submit KYC document details (status becomes PENDING)")
    public CustomerResponse submitKyc(@Valid @RequestBody KycSubmissionRequest request) {
        return customerService.submitKyc(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    @Operation(summary = "Search customers: ?name=&email=&kycStatus=&page=0&size=20&sort=createdAt,desc")
    public PageResponse<CustomerResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) KycStatus kycStatus,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return customerService.search(name, email, kycStatus, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer: owner, ADMIN, AGENT or SERVICE")
    public CustomerResponse get(@PathVariable Long id) {
        return customerService.getById(id);
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT','SERVICE')")
    @Operation(summary = "Lookup by auth user id (used by other services)")
    public CustomerResponse getByUserId(@PathVariable Long userId) {
        return customerService.getByUserId(userId);
    }

    @PatchMapping("/{id}/kyc")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    @Operation(summary = "Verify or reject a customer's KYC")
    public CustomerResponse decideKyc(@PathVariable Long id, @Valid @RequestBody KycDecisionRequest request) {
        return customerService.decideKyc(id, request);
    }
}
