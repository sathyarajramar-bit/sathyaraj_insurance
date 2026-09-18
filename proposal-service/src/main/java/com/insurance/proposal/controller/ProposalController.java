package com.insurance.proposal.controller;

import com.insurance.common.dto.PageResponse;
import com.insurance.proposal.dto.CreateProposalRequest;
import com.insurance.proposal.dto.DecisionRequest;
import com.insurance.proposal.dto.ProposalResponse;
import com.insurance.proposal.dto.StatusHistoryResponse;
import com.insurance.proposal.dto.UpdateProposalRequest;
import com.insurance.proposal.entity.ProposalStatus;
import com.insurance.proposal.service.ProposalService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Proposals", description = "Insurance applications: from accepted quote to underwriting decision")
public class ProposalController {

    private final ProposalService proposalService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Create a DRAFT proposal from an ACCEPTED quote (prefilled from the customer profile)")
    public ResponseEntity<ProposalResponse> create(@Valid @RequestBody CreateProposalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proposalService.createFromQuote(request.quoteNumber()));
    }

    @GetMapping("/{proposalNumber}")
    @Operation(summary = "Proposal by number: owner, ADMIN, AGENT or SERVICE")
    public ProposalResponse get(@PathVariable String proposalNumber) {
        return proposalService.getByNumber(proposalNumber);
    }

    @GetMapping("/{proposalNumber}/history")
    @Operation(summary = "Status transitions with actor, time and reason")
    public List<StatusHistoryResponse> history(@PathVariable String proposalNumber) {
        return proposalService.history(proposalNumber);
    }

    @GetMapping
    @Operation(summary = "My proposals, or all with filters for ADMIN/AGENT: ?customerId=&status=&page=&size=&sort=")
    public PageResponse<ProposalResponse> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) ProposalStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return proposalService.search(customerId, status, pageable);
    }

    @PutMapping("/{proposalNumber}")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Complete a DRAFT: proposer, nominee, declarations")
    public ProposalResponse update(@PathVariable String proposalNumber, @Valid @RequestBody UpdateProposalRequest request) {
        return proposalService.update(proposalNumber, request);
    }

    @PostMapping("/{proposalNumber}/submit")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    @Operation(summary = "Submit for underwriting (idempotent: re-submitting a SUBMITTED proposal returns it unchanged)")
    public ProposalResponse submit(@PathVariable String proposalNumber) {
        return proposalService.submit(proposalNumber);
    }

    @PostMapping("/{proposalNumber}/review")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Operation(summary = "Take a SUBMITTED proposal under review")
    public ProposalResponse review(@PathVariable String proposalNumber) {
        return proposalService.startReview(proposalNumber);
    }

    @PostMapping("/{proposalNumber}/approve")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Operation(summary = "Approve (from SUBMITTED or UNDER_REVIEW); payment can then be made")
    public ProposalResponse approve(@PathVariable String proposalNumber, @RequestBody(required = false) @Valid DecisionRequest decision) {
        return proposalService.approve(proposalNumber, decision);
    }

    @PostMapping("/{proposalNumber}/reject")
    @PreAuthorize("hasAnyRole('AGENT','ADMIN')")
    @Operation(summary = "Reject with a mandatory reason")
    public ProposalResponse reject(@PathVariable String proposalNumber, @RequestBody(required = false) @Valid DecisionRequest decision) {
        return proposalService.reject(proposalNumber, decision);
    }
}
