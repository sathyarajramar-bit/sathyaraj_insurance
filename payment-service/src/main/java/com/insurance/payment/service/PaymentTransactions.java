package com.insurance.payment.service;

import com.insurance.payment.client.PaymentSuccessfulEvent;
import com.insurance.payment.entity.OutboxEvent;
import com.insurance.payment.entity.Payment;
import com.insurance.payment.entity.PaymentStatus;
import com.insurance.payment.event.OutboxDispatcher;
import com.insurance.payment.event.OutboxWriter;
import com.insurance.payment.provider.PaymentProvider;
import com.insurance.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The short transactions of the payment flow, in their own bean so the proxy applies.
 * {@link #record} commits the payment row AND the outbox row atomically, then the after-commit listener
 * in {@link OutboxDispatcher} delivers the event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTransactions {

    private final PaymentRepository paymentRepository;
    private final OutboxWriter outboxWriter;
    private final ApplicationEventPublisher applicationEvents;

    @Transactional
    public Payment record(Payment payment, PaymentProvider.ChargeResult result) {
        payment.setCompletedAt(Instant.now());
        if (result.success()) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setProviderTxnId(result.providerTxnId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
        }
        Payment saved;
        try {
            saved = paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request with the same Idempotency-Key won the race: answer with its row.
            return paymentRepository.findByIdempotencyKey(payment.getIdempotencyKey()).orElseThrow(() -> e);
        }
        log.info("Payment {} {} ({} {}) for {}", saved.getPaymentReference(), saved.getStatus(), saved.getAmount(),
                saved.getCurrency(), saved.getProposalNumber() != null ? saved.getProposalNumber() : saved.getPolicyNumber());
        if (saved.getStatus() == PaymentStatus.SUCCESS) {
            OutboxEvent outbox = outboxWriter.write(new PaymentSuccessfulEvent(saved.getPaymentReference(), saved.getPurpose().name(),
                    saved.getProposalNumber(), saved.getPolicyNumber(), saved.getUserId(), saved.getCustomerId(),
                    saved.getAmount(), saved.getCurrency(), saved.getCompletedAt()));
            applicationEvents.publishEvent(new OutboxDispatcher.OutboxCommittedEvent(outbox.getId()));
        }
        return saved;
    }

    @Transactional
    public Payment recordRefund(Long paymentId, String refundReference, String reason) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundReference(refundReference);
        payment.setRefundReason(reason);
        payment.setRefundedAt(Instant.now());
        log.info("Payment {} refunded ({})", payment.getPaymentReference(), reason);
        return payment;
    }
}
