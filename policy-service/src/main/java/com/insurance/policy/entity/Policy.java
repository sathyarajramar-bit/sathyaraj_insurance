package com.insurance.policy.entity;

import com.insurance.common.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Policy extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true, length = 30)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "proposal_number", nullable = false, unique = true, length = 30)
    private String proposalNumber;

    @Column(name = "quote_number", nullable = false, length = 30)
    private String quoteNumber;

    @Column(name = "payment_reference", nullable = false, unique = true, length = 30)
    private String paymentReference;

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

    @Column(name = "holder_name", nullable = false, length = 200)
    private String holderName;

    @Column(name = "holder_email", nullable = false)
    private String holderEmail;

    @Column(name = "nominee_name", length = 150)
    private String nomineeName;

    @Column(name = "nominee_relationship", length = 20)
    private String nomineeRelationship;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "renewed_from_policy_number", length = 30)
    private String renewedFromPolicyNumber;

    @Column(name = "renewed_by_policy_number", length = 30)
    private String renewedByPolicyNumber;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "schedule_document_id")
    private Long scheduleDocumentId;

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    /** Coverage is in force on the given date: ACTIVE and within [start, end]. */
    public boolean coversOn(LocalDate date) {
        return status == PolicyStatus.ACTIVE && !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
