package com.insurance.policy.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.policy.dto.CancelPolicyRequest;
import com.insurance.policy.dto.IssuePolicyRequest;
import com.insurance.policy.dto.PolicyResponse;
import com.insurance.policy.entity.PolicyStatus;
import com.insurance.policy.mapper.PolicyMapper;
import com.insurance.policy.service.PolicyIssuanceService;
import com.insurance.policy.service.PolicyService;
import com.insurance.policy.service.RenewalPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Policies", description = "Issued policies, renewals and cancellations")
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyIssuanceService issuanceService;
    private final PolicyMapper mapper;
    private final RenewalPolicy renewalPolicy;

    @PostMapping("/issue")
    @PreAuthorize("hasRole('SERVICE')")
    @Operation(summary = "Internal: issue a policy from a PaymentSuccessful event (idempotent by paymentReference; payment is re-verified)")
    public PolicyResponse issue(@Valid @RequestBody IssuePolicyRequest request) {
        return mapper.toResponse(issuanceService.issue(request), renewalPolicy);
    }

    @GetMapping("/{policyNumber}")
    @Operation(summary = "Policy by number: owner, ADMIN, AGENT, CLAIMS_HANDLER or SERVICE; includes renewal eligibility")
    public PolicyResponse get(@PathVariable String policyNumber) {
        return policyService.getByNumber(policyNumber);
    }

    @GetMapping("/{policyNumber}/coverage-check")
    @PreAuthorize("hasAnyRole('SERVICE','ADMIN','AGENT','CLAIMS_HANDLER')")
    @Operation(summary = "Internal (claims-service): the policy if it is in force on the incident date, else 422")
    public PolicyResponse coverageCheck(@PathVariable String policyNumber,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentDate) {
        return policyService.getForClaim(policyNumber, incidentDate);
    }

    @GetMapping
    @Operation(summary = "My policies, or all with filters (ADMIN/AGENT): ?status=&customerId=&registrationNumber=&page=&size=&sort=")
    public PageResponse<PolicyResponse> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) String registrationNumber,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return policyService.search(customerId, status, registrationNumber, pageable);
    }

    @PostMapping("/{policyNumber}/cancel")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Cancel an ACTIVE policy (owner, AGENT or ADMIN)")
    public PolicyResponse cancel(@PathVariable String policyNumber, @Valid @RequestBody CancelPolicyRequest request) {
        return policyService.cancel(policyNumber, request.reason());
    }

    @PostMapping("/{policyNumber}/renew")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Renewal eligibility and instructions: the renewal itself is completed by paying with policyNumber in payment-service")
    public PolicyResponse renew(@PathVariable String policyNumber) {
        return policyService.getByNumber(policyNumber);
    }
}
