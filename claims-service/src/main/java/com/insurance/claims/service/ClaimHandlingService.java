package com.insurance.claims.service;

import com.insurance.claims.dto.ApproveClaimRequest;
import com.insurance.claims.dto.AssessmentRequest;
import com.insurance.claims.dto.ClaimResponse;
import com.insurance.claims.dto.SettleClaimRequest;
import com.insurance.claims.entity.Claim;
import com.insurance.claims.entity.ClaimStatus;
import com.insurance.claims.exception.ClaimException;
import com.insurance.claims.mapper.ClaimMapper;
import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.common.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Handler/admin side of the claim lifecycle. Every method validates the transition through the state
 * machine and records the audit row; customer-visible decisions send notifications. Rules: approved
 * amount <= claimed amount and <= IDV; settlement only after approval (the state machine has no other
 * path to SETTLED); a CLOSED claim is immutable except through the explicit reopen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimHandlingService {

    private final ClaimService claims;
    private final ClaimMapper mapper;
    private final NotificationPublisher notifications;

    @Transactional
    public ClaimResponse startReview(String claimNumber) {
        Claim claim = claims.find(claimNumber);
        move(claim, ClaimStatus.UNDER_REVIEW, null);
        claim.setAssessor(actor());
        return mapper.toResponse(claim);
    }

    @Transactional
    public ClaimResponse assess(String claimNumber, AssessmentRequest request) {
        Claim claim = claims.find(claimNumber);
        if (claim.getStatus() != ClaimStatus.UNDER_REVIEW) {
            throw new ClaimException("Assessment is only possible while UNDER_REVIEW (status " + claim.getStatus() + ")");
        }
        if (request.assessedAmount() != null && request.assessedAmount().compareTo(claim.getIdv()) > 0) {
            throw new ClaimException("Assessed amount cannot exceed the insured value " + claim.getIdv());
        }
        claim.setAssessedAmount(request.assessedAmount());
        claim.setAssessmentNotes(request.notes());
        claim.setAssessor(actor());
        return mapper.toResponse(claim);
    }

    @Transactional
    public ClaimResponse requireDocuments(String claimNumber, String documentsRequested) {
        Claim claim = claims.find(claimNumber);
        move(claim, ClaimStatus.DOCUMENTS_REQUIRED, documentsRequested);
        claim.setDocumentsRequested(documentsRequested);
        claim.setDocumentsRequestedAt(Instant.now());
        notify(claim, NotificationEventType.CLAIM_DOCUMENTS_REQUIRED, Map.of("documentsRequested", documentsRequested));
        return mapper.toResponse(claim);
    }

    @Transactional
    public ClaimResponse approve(String claimNumber, ApproveClaimRequest request) {
        Claim claim = claims.find(claimNumber);
        if (request.approvedAmount().compareTo(claim.getClaimedAmount()) > 0) {
            throw new ClaimException("Approved amount cannot exceed the claimed amount " + claim.getClaimedAmount());
        }
        if (request.approvedAmount().compareTo(claim.getIdv()) > 0) {
            throw new ClaimException("Approved amount cannot exceed the insured value " + claim.getIdv());
        }
        move(claim, ClaimStatus.APPROVED, request.reason());
        claim.setApprovedAmount(request.approvedAmount());
        claim.setDecisionReason(request.reason());
        notify(claim, NotificationEventType.CLAIM_APPROVED, Map.of("approvedAmount", request.approvedAmount().toPlainString()));
        return mapper.toResponse(claim);
    }

    @Transactional
    public ClaimResponse reject(String claimNumber, String reason) {
        Claim claim = claims.find(claimNumber);
        move(claim, ClaimStatus.REJECTED, reason);
        claim.setDecisionReason(reason);
        notify(claim, NotificationEventType.CLAIM_REJECTED, Map.of("reason", reason));
        return mapper.toResponse(claim);
    }

    @Transactional
    public ClaimResponse settle(String claimNumber, SettleClaimRequest request) {
        Claim claim = claims.find(claimNumber);
        move(claim, ClaimStatus.SETTLED, "Settled via " + request.settlementReference());
        claim.setSettledAmount(claim.getApprovedAmount());
        claim.setSettlementReference(request.settlementReference());
        claim.setSettledAt(Instant.now());
        notify(claim, NotificationEventType.CLAIM_SETTLED, Map.of("settledAmount", claim.getSettledAmount().toPlainString(),
                "settlementReference", request.settlementReference()));
        return mapper.toResponse(claim);
    }

    @Transactional
    public ClaimResponse close(String claimNumber, String reason) {
        Claim claim = claims.find(claimNumber);
        move(claim, ClaimStatus.CLOSED, reason);
        claim.setClosedAt(Instant.now());
        return mapper.toResponse(claim);
    }

    /** Explicit reopen (ADMIN only, reason mandatory): CLOSED -> UNDER_REVIEW, counted on the claim. */
    @Transactional
    public ClaimResponse reopen(String claimNumber, String reason) {
        Claim claim = claims.find(claimNumber);
        if (claim.getStatus() != ClaimStatus.CLOSED) {
            throw new ClaimException("Only CLOSED claims can be reopened (status " + claim.getStatus() + ")");
        }
        claim.reopen(actor(), reason);
        log.info("Claim {} reopened by {}: {}", claimNumber, actor(), reason);
        return mapper.toResponse(claim);
    }

    private void move(Claim claim, ClaimStatus target, String reason) {
        if (!claim.getStatus().canTransitionTo(target)) {
            throw new ClaimException("Claim " + claim.getClaimNumber() + " cannot move from " + claim.getStatus() + " to " + target);
        }
        claim.transition(target, actor(), reason);
        log.info("Claim {} -> {} by {}", claim.getClaimNumber(), target, actor());
    }

    private void notify(Claim claim, NotificationEventType type, Map<String, String> extra) {
        Map<String, String> params = new HashMap<>(extra);
        params.put("claimNumber", claim.getClaimNumber());
        params.put("policyNumber", claim.getPolicyNumber());
        notifications.publish(NotificationRequest.of(type, claim.getUserId(), claim.getCustomerId(), null, null, "CLAIM", claim.getClaimNumber(), params));
    }

    private static String actor() {
        return "user:" + CurrentUser.require().userId();
    }
}
