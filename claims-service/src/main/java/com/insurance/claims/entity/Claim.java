package com.insurance.claims.entity;

import com.insurance.common.jpa.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "claims")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Claim extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true, length = 30)
    private String claimNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private ClaimStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "policy_number", nullable = false, length = 30)
    private String policyNumber;

    @Column(name = "product_code", nullable = false, length = 40)
    private String productCode;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal idv;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 20)
    private ClaimType claimType;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location", nullable = false)
    private String incidentLocation;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(name = "claimed_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal claimedAmount;

    @Column(name = "assessed_amount", precision = 12, scale = 2)
    private BigDecimal assessedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "settled_amount", precision = 12, scale = 2)
    private BigDecimal settledAmount;

    @Column(name = "settlement_reference", length = 64)
    private String settlementReference;

    @Column(length = 64)
    private String assessor;

    @Column(name = "assessment_notes", length = 2000)
    private String assessmentNotes;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "documents_requested", length = 500)
    private String documentsRequested;

    @Column(name = "documents_requested_at")
    private Instant documentsRequestedAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "reopened_count", nullable = false)
    private int reopenedCount;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("attachedAt ASC, id ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<ClaimDocument> documents = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<ClaimStatusHistory> history = new ArrayList<>();

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** The one place a status changes: validates the transition and records the audit row. */
    public void transition(ClaimStatus target, String changedBy, String reason) {
        if (status != null && !status.canTransitionTo(target)) {
            throw new IllegalStateException("Claim " + claimNumber + " cannot move from " + status + " to " + target);
        }
        record(target, changedBy, reason);
    }

    /** Reopen bypasses the normal state machine on purpose (CLOSED is otherwise terminal). */
    public void reopen(String changedBy, String reason) {
        reopenedCount++;
        closedAt = null;
        record(ClaimStatus.UNDER_REVIEW, changedBy, "REOPENED: " + reason);
    }

    private void record(ClaimStatus target, String changedBy, String reason) {
        history.add(ClaimStatusHistory.builder().claim(this).fromStatus(status).toStatus(target)
                .changedBy(changedBy).reason(reason).changedAt(Instant.now()).build());
        status = target;
    }

    public void attach(ClaimDocument document) {
        document.setClaim(this);
        documents.add(document);
    }
}
