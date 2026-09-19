package com.insurance.policy.repository;

import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PolicyRepository extends JpaRepository<Policy, Long>, JpaSpecificationExecutor<Policy> {

    Optional<Policy> findByPolicyNumber(String policyNumber);

    Optional<Policy> findByPaymentReference(String paymentReference);

    List<Policy> findByStatusAndEndDate(PolicyStatus status, LocalDate endDate);

    List<Policy> findByStatusAndEndDateBefore(PolicyStatus status, LocalDate before);

    @Modifying(clearAutomatically = true)
    @Query("update Policy p set p.status = :expired, p.updatedAt = :now, p.updatedBy = 'policy-expiry-job' "
            + "where p.status = :active and p.endDate < :today")
    int expireOverdue(@Param("today") LocalDate today, @Param("now") Instant now,
                      @Param("active") PolicyStatus active, @Param("expired") PolicyStatus expired);
}
