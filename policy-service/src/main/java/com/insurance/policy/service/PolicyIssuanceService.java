package com.insurance.policy.service;

import com.insurance.common.exception.PolicyException;
import com.insurance.common.notification.NotificationEventType;
import com.insurance.common.notification.NotificationPublisher;
import com.insurance.common.notification.NotificationRequest;
import com.insurance.policy.client.DownstreamGateways;
import com.insurance.policy.client.PaymentClient;
import com.insurance.policy.client.ProposalClient;
import com.insurance.policy.dto.IssuePolicyRequest;
import com.insurance.policy.entity.Policy;
import com.insurance.policy.entity.PolicyStatus;
import com.insurance.policy.exception.PolicyNotFoundException;
import com.insurance.policy.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Consumer of PaymentSuccessful. Idempotent by payment reference: redelivery returns the existing policy.
 *
 * <p>"Policy must NOT be issued before successful payment": the request carries a payment reference, and
 * this service re-reads that payment from payment-service and requires status SUCCESS with a matching
 * proposal/policy. A forged or replayed event without a real successful payment is rejected.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyIssuanceService {

    private final PolicyRepository policyRepository;
    private final DownstreamGateways gateways;
    private final PolicyNumberGenerator numberGenerator;
    private final RenewalPolicy renewalPolicy;
    private final PolicyDocumentService documents;
    private final NotificationPublisher notifications;
    private final PolicyTransactions transactions;

    public Policy issue(IssuePolicyRequest request) {
        return policyRepository.findByPaymentReference(request.paymentReference())
                .map(existing -> {
                    log.info("Issue request for payment {} replayed: returning policy {}", request.paymentReference(), existing.getPolicyNumber());
                    return existing;
                })
                .orElseGet(() -> issueNew(request));
    }

    private Policy issueNew(IssuePolicyRequest request) {
        PaymentClient.PaymentView payment = gateways.payment(request.paymentReference());
        if (!"SUCCESS".equals(payment.status())) {
            throw new PolicyException("Payment " + payment.paymentReference() + " is " + payment.status() + "; a policy requires a SUCCESS payment");
        }
        Policy policy = "RENEWAL".equals(payment.purpose()) ? renewal(payment) : fresh(payment);
        Policy saved;
        try {
            saved = transactions.persist(policy);
        } catch (DataIntegrityViolationException e) {
            // Concurrent redelivery inserted it first: idempotent outcome.
            return policyRepository.findByPaymentReference(request.paymentReference()).orElseThrow(() -> e);
        }
        documents.attachSchedule(saved);
        notifications.publish(NotificationRequest.of(NotificationEventType.POLICY_ISSUED, saved.getUserId(), saved.getCustomerId(),
                saved.getHolderEmail(), null, "POLICY", saved.getPolicyNumber(),
                Map.of("policyNumber", saved.getPolicyNumber(), "product", saved.getProductName(), "startDate", saved.getStartDate().toString(),
                        "endDate", saved.getEndDate().toString(), "premium", saved.getPremiumAmount().toPlainString())));
        return saved;
    }

    private Policy fresh(PaymentClient.PaymentView payment) {
        ProposalClient.ProposalView proposal = gateways.proposal(payment.proposalNumber());
        if (!"APPROVED".equals(proposal.status())) {
            throw new PolicyException("Proposal " + proposal.proposalNumber() + " is " + proposal.status() + "; only APPROVED proposals become policies");
        }
        LocalDate start = LocalDate.now();
        return Policy.builder()
                .policyNumber(numberGenerator.next(proposal.productCode())).status(PolicyStatus.ACTIVE)
                .userId(proposal.userId()).customerId(proposal.customerId())
                .proposalNumber(proposal.proposalNumber()).quoteNumber(proposal.quoteNumber()).paymentReference(payment.paymentReference())
                .productId(proposal.productId()).productCode(proposal.productCode()).productName(proposal.productName())
                .coverageType(proposal.coverageType()).termMonths(proposal.termMonths())
                .vehicleId(proposal.vehicleId()).registrationNumber(proposal.registrationNumber())
                .idv(proposal.idv()).premiumAmount(payment.amount())
                .holderName(proposal.proposer().firstName() + " " + proposal.proposer().lastName()).holderEmail(proposal.proposer().email())
                .nomineeName(proposal.nominee() == null ? null : proposal.nominee().name())
                .nomineeRelationship(proposal.nominee() == null ? null : proposal.nominee().relationship())
                .startDate(start).endDate(start.plusMonths(proposal.termMonths()).minusDays(1)).issuedAt(Instant.now())
                .build();
    }

    /** Renewal: new policy row copied from the old one; cover continues from the old end date when renewed in time. */
    private Policy renewal(PaymentClient.PaymentView payment) {
        Policy old = policyRepository.findByPolicyNumber(payment.policyNumber())
                .orElseThrow(() -> new PolicyNotFoundException(payment.policyNumber()));
        RenewalPolicy.Result eligibility = renewalPolicy.evaluate(old);
        if (!eligibility.eligible()) {
            throw new PolicyException("Policy " + old.getPolicyNumber() + " cannot be renewed: " + eligibility.message());
        }
        LocalDate today = LocalDate.now();
        LocalDate start = old.getEndDate().isBefore(today) ? today : old.getEndDate().plusDays(1);
        return Policy.builder()
                .policyNumber(numberGenerator.next(old.getProductCode())).status(PolicyStatus.ACTIVE)
                .userId(old.getUserId()).customerId(old.getCustomerId())
                .proposalNumber(old.getProposalNumber() + "/R" + (old.getRenewedFromPolicyNumber() == null ? 1 : 2))
                .quoteNumber(old.getQuoteNumber()).paymentReference(payment.paymentReference())
                .productId(old.getProductId()).productCode(old.getProductCode()).productName(old.getProductName())
                .coverageType(old.getCoverageType()).termMonths(old.getTermMonths())
                .vehicleId(old.getVehicleId()).registrationNumber(old.getRegistrationNumber())
                .idv(old.getIdv()).premiumAmount(payment.amount())
                .holderName(old.getHolderName()).holderEmail(old.getHolderEmail())
                .nomineeName(old.getNomineeName()).nomineeRelationship(old.getNomineeRelationship())
                .startDate(start).endDate(start.plusMonths(old.getTermMonths()).minusDays(1)).issuedAt(Instant.now())
                .renewedFromPolicyNumber(old.getPolicyNumber())
                .build();
    }
}
