package com.insurance.claims.service;

import com.insurance.claims.client.ClaimsGateways;
import com.insurance.claims.client.DocumentClient;
import com.insurance.claims.client.PolicyClient;
import com.insurance.claims.config.ClaimsProperties;
import com.insurance.claims.dto.AttachDocumentsRequest;
import com.insurance.claims.dto.ClaimHistoryResponse;
import com.insurance.claims.dto.ClaimResponse;
import com.insurance.claims.dto.RegisterClaimRequest;
import com.insurance.claims.entity.Claim;
import com.insurance.claims.entity.ClaimDocument;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.entity.ClaimType;
import com.insurance.claims.exception.ClaimException;
import com.insurance.claims.exception.ClaimNotFoundException;
import com.insurance.claims.mapper.ClaimMapper;
import com.insurance.claims.repository.ClaimRepository;
import com.insurance.claims.repository.ClaimSpecifications;
import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Customer-facing claim use cases: register, read, list, attach documents.
 * Registration rules: policy covers the incident date (policy-service decides), the policy belongs to
 * the claimant, the incident is not older than claims.max-days-after-incident, the claimed amount does
 * not exceed the IDV, and the claim type matches the coverage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimService {

    private final ClaimRepository claimRepository;
    private final ClaimsGateways gateways;
    private final ClaimNumberGenerator numberGenerator;
    private final ClaimAccessPolicy accessPolicy;
    private final ClaimMapper mapper;
    private final ClaimsProperties properties;
    private final NotificationPublisher notifications;

    public ClaimResponse register(RegisterClaimRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        if (ChronoUnit.DAYS.between(request.incidentDate(), LocalDate.now()) > properties.getMaxDaysAfterIncident()) {
            throw new ClaimException("Claims must be registered within " + properties.getMaxDaysAfterIncident() + " days of the incident");
        }
        PolicyClient.PolicyView policy = gateways.coverageCheck(request.policyNumber(), request.incidentDate());
        if (!policy.userId().equals(user.userId()) && !accessPolicy.isStaff(user) && !user.hasRole("AGENT")) {
            throw new ForbiddenException("Policy " + policy.policyNumber() + " does not belong to you");
        }
        if (request.claimedAmount().compareTo(policy.idv()) > 0) {
            throw new ClaimException("Claimed amount " + request.claimedAmount() + " exceeds the insured value " + policy.idv());
        }
        if (request.claimType() == ClaimType.THIRD_PARTY && "OWN_DAMAGE".equals(policy.coverageType())) {
            throw new ClaimException("Policy " + policy.policyNumber() + " has no third party cover");
        }
        if (request.claimType() != ClaimType.THIRD_PARTY && "THIRD_PARTY".equals(policy.coverageType())) {
            throw new ClaimException("Policy " + policy.policyNumber() + " covers third party liability only");
        }
        Claim claim = Claim.builder()
                .claimNumber(numberGenerator.next())
                .userId(policy.userId()).customerId(policy.customerId())
                .policyNumber(policy.policyNumber()).productCode(policy.productCode())
                .registrationNumber(policy.registrationNumber()).idv(policy.idv())
                .claimType(request.claimType()).incidentDate(request.incidentDate())
                .incidentLocation(request.incidentLocation()).description(request.description())
                .claimedAmount(request.claimedAmount())
                .build();
        claim.transition(ClaimStatus.REGISTERED, actor(user), null);
        Claim saved = claimRepository.save(claim);
        log.info("Claim {} registered on policy {} ({} on {}, claimed {})", saved.getClaimNumber(), saved.getPolicyNumber(),
                saved.getClaimType(), saved.getIncidentDate(), saved.getClaimedAmount());
        notifications.publish(NotificationRequest.of(NotificationEventType.CLAIM_REGISTERED, saved.getUserId(), saved.getCustomerId(), null, null,
                "CLAIM", saved.getClaimNumber(), Map.of("claimNumber", saved.getClaimNumber(), "policyNumber", saved.getPolicyNumber(),
                        "claimedAmount", saved.getClaimedAmount().toPlainString())));
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getByNumber(String claimNumber) {
        Claim claim = find(claimNumber);
        accessPolicy.assertCanRead(CurrentUser.require(), claim);
        return mapper.toResponse(claim);
    }

    @Transactional(readOnly = true)
    public List<ClaimHistoryResponse> history(String claimNumber) {
        Claim claim = find(claimNumber);
        accessPolicy.assertCanRead(CurrentUser.require(), claim);
        return claim.getHistory().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ClaimResponse> search(Long customerId, String policyNumber, ClaimStatus status, ClaimType type,
                                              LocalDate incidentFrom, LocalDate incidentTo, Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        Long userFilter = user.hasAnyRole("ADMIN", "CLAIMS_HANDLER", "AGENT") ? null : user.userId();
        if (userFilter != null && customerId != null) {
            throw new ForbiddenException("Customers can only list their own claims");
        }
        return PageResponse.from(claimRepository.findAll(ClaimSpecifications.withFilters(userFilter, customerId, policyNumber, status, type,
                incidentFrom, incidentTo), pageable), mapper::toResponse);
    }

    /**
     * Attaches documents uploaded to document-service. Each id is verified: it must exist, reference this
     * claim, and (for customers) be owned by the claimant. A claim waiting for documents moves back to
     * UNDER_REVIEW automatically.
     */
    @Transactional
    public ClaimResponse attachDocuments(String claimNumber, AttachDocumentsRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        Claim claim = find(claimNumber);
        accessPolicy.assertOwnerOrStaff(user, claim);
        if (claim.getStatus() == ClaimStatus.CLOSED || claim.getStatus() == ClaimStatus.SETTLED || claim.getStatus() == ClaimStatus.REJECTED) {
            throw new ClaimException("Documents cannot be added to a " + claim.getStatus() + " claim");
        }
        for (Long documentId : request.documentIds()) {
            if (claim.getDocuments().stream().anyMatch(d -> d.getDocumentId().equals(documentId))) {
                continue;
            }
            DocumentClient.DocumentView doc = gateways.document(documentId);
            if (!"CLAIM".equals(doc.referenceType()) || !claimNumber.equals(doc.referenceNumber())) {
                throw new ClaimException("Document " + documentId + " was not uploaded for claim " + claimNumber);
            }
            if (!accessPolicy.isStaff(user) && !doc.ownerUserId().equals(user.userId())) {
                throw new ForbiddenException("Document " + documentId + " does not belong to you");
            }
            claim.attach(ClaimDocument.builder().documentId(documentId).documentType(doc.documentType()).fileName(doc.fileName())
                    .attachedBy(actor(user)).attachedAt(Instant.now()).build());
        }
        if (claim.getStatus() == ClaimStatus.DOCUMENTS_REQUIRED) {
            claim.transition(ClaimStatus.UNDER_REVIEW, actor(user), "Documents received");
            claim.setDocumentsRequestedAt(null);
        }
        return mapper.toResponse(claim);
    }

    Claim find(String claimNumber) {
        return claimRepository.findByClaimNumber(claimNumber).orElseThrow(() -> new ClaimNotFoundException(claimNumber));
    }

    static String actor(AuthenticatedUser user) {
        return user.hasRole("SERVICE") ? user.email() : "user:" + user.userId();
    }
}
