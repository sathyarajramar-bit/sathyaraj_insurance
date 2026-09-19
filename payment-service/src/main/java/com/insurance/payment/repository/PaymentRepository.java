package com.insurance.payment.repository;

import com.insurance.payment.entity.Payment;
import com.insurance.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByPaymentReference(String paymentReference);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    boolean existsByProposalNumberAndStatus(String proposalNumber, PaymentStatus status);

    boolean existsByPolicyNumberAndStatus(String policyNumber, PaymentStatus status);
}
