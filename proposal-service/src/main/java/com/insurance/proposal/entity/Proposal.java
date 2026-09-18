package com.insurance.proposal.entity;

import com.insurance.common.jpa.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "proposals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Proposal extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proposal_number", nullable = false, unique = true, length = 30)
    private String proposalNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProposalStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "quote_number", nullable = false, unique = true, length = 30)
    private String quoteNumber;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_code", nullable = false, length = 40)
    private String productCode;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(name = "coverage_type", nullable = false, length = 20)
    private String coverageType;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "vehicle_id", nullable = false)
    private Long vehicleId;

    @Column(name = "registration_number", nullable = false, length = 20)
    private String registrationNumber;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal idv;

    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Embedded
    private Proposer proposer;

    @Embedded
    private Nominee nominee;

    @Column(name = "declarations_accepted", nullable = false)
    private boolean declarationsAccepted;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @OneToMany(mappedBy = "proposal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    @BatchSize(size = 20)
    @Builder.Default
    private List<ProposalStatusHistory> history = new ArrayList<>();

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** The single place a status changes: validates the transition and appends the audit row. */
    public void transition(ProposalStatus target, String changedBy, String reason) {
        if (status != null && !status.canTransitionTo(target)) {
            throw new IllegalStateException("Proposal " + proposalNumber + " cannot move from " + status + " to " + target);
        }
        ProposalStatusHistory entry = ProposalStatusHistory.builder()
                .proposal(this).fromStatus(status).toStatus(target).changedBy(changedBy).reason(reason)
                .changedAt(Instant.now()).build();
        history.add(entry);
        status = target;
    }
}
