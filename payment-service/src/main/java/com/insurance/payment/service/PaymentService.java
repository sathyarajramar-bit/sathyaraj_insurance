package com.insurance.payment.service;

import com.insurance.common.dto.PageResponse;
import com.insurance.common.exception.ForbiddenException;
import com.insurance.common.exception.PaymentException;
import com.insurance.common.exception.ValidationException;
import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.common.security.AuthenticatedUser;
import com.insurance.common.security.CurrentUser;
import com.insurance.payment.client.PolicyClient;
import com.insurance.payment.client.PolicyGateway;
import com.insurance.payment.client.ProposalClient;
import com.insurance.payment.client.ProposalGateway;
import com.insurance.payment.config.PaymentProperties;
import com.insurance.payment.dto.PaymentRequest;
import com.insurance.payment.dto.PaymentResponse;
import com.insurance.payment.entity.Payment;
import com.insurance.payment.entity.PaymentPurpose;
import com.insurance.payment.entity.PaymentStatus;
import com.insurance.payment.exception.PaymentNotFoundException;
import com.insurance.payment.mapper.PaymentMapper;
import com.insurance.payment.provider.PaymentProvider;
import com.insurance.payment.repository.PaymentRepository;
import com.insurance.payment.repository.PaymentSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Payment use cases.
 *
 * <p>Idempotency: the client sends an Idempotency-Key; the key is stored with a unique constraint. A retry
 * with the same key returns the original payment (whatever its status) without touching the provider.
 * Independently of the key, a proposal that already has a SUCCESS payment cannot be charged again.
 *
 * <p>Transaction boundary: the provider call happens OUTSIDE any transaction (network call with money on
 * the line). The result, the outbox row and the "committed" signal are then written in one short
 * transaction by {@link PaymentTransactions}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ProposalGateway proposalGateway;
    private final PolicyGateway policyGateway;
    private final PaymentProvider provider;
    private final PaymentReferenceGenerator referenceGenerator;
    private final PaymentMapper mapper;
    private final PaymentProperties properties;
    private final NotificationPublisher notifications;
    private final PaymentTransactions transactions;

    public PaymentResponse pay(String idempotencyKey, PaymentRequest request) {
        AuthenticatedUser user = CurrentUser.require();
        Optional<Payment> replay = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            log.info("Idempotent replay of payment {} for key {}", replay.get().getPaymentReference(), idempotencyKey);
            return mapper.toResponse(replay.get());
        }
        Payment payment = prepare(user, idempotencyKey, request);
        PaymentProvider.ChargeResult result;
        try {
            result = provider.charge(new PaymentProvider.ChargeRequest(payment.getPaymentReference(), payment.getAmount(),
                    payment.getCurrency(), payment.getPaymentMethod().name(), request.instrument()));
        } catch (RuntimeException e) {
            log.error("Provider {} failed for {}: {}", provider.name(), payment.getPaymentReference(), e.toString());
            result = new PaymentProvider.ChargeResult(false, null, "Payment gateway error: " + e.getMessage());
        }
        Payment saved = transactions.record(payment, result);
        notify(saved);
        return mapper.toResponse(saved);
    }

    private Payment prepare(AuthenticatedUser user, String idempotencyKey, PaymentRequest request) {
        boolean forProposal = request.proposalNumber() != null && !request.proposalNumber().isBlank();
        boolean forPolicy = request.policyNumber() != null && !request.policyNumber().isBlank();
        if (forProposal == forPolicy) {
            throw new ValidationException("Provide exactly one of proposalNumber (new policy) or policyNumber (renewal)");
        }
        Payment.PaymentBuilder builder = Payment.builder()
                .paymentReference(referenceGenerator.next()).idempotencyKey(idempotencyKey)
                .status(PaymentStatus.INITIATED).currency(properties.getCurrency())
                .paymentMethod(request.paymentMethod()).provider(provider.name());
        if (forProposal) {
            ProposalClient.ProposalView proposal = proposalGateway.get(request.proposalNumber());
            assertOwner(user, proposal.userId());
            if (!"APPROVED".equals(proposal.status())) {
                throw new PaymentException("Proposal " + proposal.proposalNumber() + " must be APPROVED before payment (status " + proposal.status() + ")");
            }
            if (paymentRepository.existsByProposalNumberAndStatus(proposal.proposalNumber(), PaymentStatus.SUCCESS)) {
                throw new PaymentException("Proposal " + proposal.proposalNumber() + " has already been paid");
            }
            return builder.purpose(PaymentPurpose.NEW_POLICY).proposalNumber(proposal.proposalNumber())
                    .userId(proposal.userId()).customerId(proposal.customerId()).amount(proposal.premiumAmount()).build();
        }
        PolicyClient.PolicyView policy = policyGateway.get(request.policyNumber());
        assertOwner(user, policy.userId());
        if (!policy.renewalEligible()) {
            throw new PaymentException("Policy " + policy.policyNumber() + " cannot be renewed: " + policy.renewalMessage());
        }
        if (paymentRepository.existsByPolicyNumberAndStatus(policy.policyNumber(), PaymentStatus.SUCCESS)) {
            throw new PaymentException("A renewal payment for policy " + policy.policyNumber() + " already exists");
        }
        return builder.purpose(PaymentPurpose.RENEWAL).policyNumber(policy.policyNumber())
                .userId(policy.userId()).customerId(policy.customerId()).amount(policy.premiumAmount()).build();
    }

    private static void assertOwner(AuthenticatedUser user, Long ownerUserId) {
        if (!ownerUserId.equals(user.userId()) && !user.hasAnyRole("ADMIN", "AGENT")) {
            throw new ForbiddenException("You can only pay for your own proposals and policies");
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByReference(String reference) {
        Payment payment = find(reference);
        AuthenticatedUser user = CurrentUser.require();
        if (!payment.isOwnedBy(user.userId()) && !user.hasAnyRole("ADMIN", "AGENT", "SERVICE")) {
            throw new ForbiddenException("You are not allowed to access this payment");
        }
        return mapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(String proposalNumber, PaymentStatus status, Pageable pageable) {
        AuthenticatedUser user = CurrentUser.require();
        Long userFilter = user.hasAnyRole("ADMIN", "AGENT") ? null : user.userId();
        return PageResponse.from(paymentRepository.findAll(PaymentSpecifications.withFilters(userFilter, proposalNumber, status), pageable),
                mapper::toResponse);
    }

    public PaymentResponse refund(String reference, String reason) {
        Payment payment = find(reference);
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentException("Only SUCCESS payments can be refunded (status " + payment.getStatus() + ")");
        }
        PaymentProvider.RefundResult result = provider.refund(payment.getProviderTxnId(), payment.getAmount(), reason);
        if (!result.success()) {
            throw new PaymentException("Refund failed: " + result.failureReason());
        }
        Payment saved = transactions.recordRefund(payment.getId(), result.refundReference(), reason);
        notifications.publish(NotificationRequest.of(NotificationEventType.PAYMENT_REFUNDED, saved.getUserId(), saved.getCustomerId(),
                null, null, "PAYMENT", saved.getPaymentReference(), Map.of("amount", saved.getAmount().toPlainString(), "reason", reason)));
        return mapper.toResponse(saved);
    }

    Payment find(String reference) {
        return paymentRepository.findByPaymentReference(reference).orElseThrow(() -> new PaymentNotFoundException(reference));
    }

    private void notify(Payment p) {
        boolean success = p.getStatus() == PaymentStatus.SUCCESS;
        String ref = p.getProposalNumber() != null ? p.getProposalNumber() : p.getPolicyNumber();
        notifications.publish(NotificationRequest.of(success ? NotificationEventType.PAYMENT_SUCCESS : NotificationEventType.PAYMENT_FAILED,
                p.getUserId(), p.getCustomerId(), null, null, "PAYMENT", p.getPaymentReference(),
                Map.of("amount", p.getAmount().toPlainString(), "currency", p.getCurrency(), "reference", ref == null ? "" : ref,
                        "reason", p.getFailureReason() == null ? "" : p.getFailureReason())));
    }
}
