package com.insurance.claims.repository;

import com.insurance.claims.entity.Claim;
import com.insurance.claims.entity.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByClaimNumber(String claimNumber);

    boolean existsByPolicyNumberAndStatusIn(String policyNumber, List<ClaimStatus> statuses);

    List<Claim> findByStatusAndDocumentsRequestedAtBefore(ClaimStatus status, Instant before);
}
