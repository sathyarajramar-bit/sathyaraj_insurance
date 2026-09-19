package com.insurance.payment.dto;

import com.insurance.payment.entity.PaymentMethod;
import com.insurance.payment.entity.PaymentPurpose;
import com.insurance.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, String paymentReference, PaymentStatus status, PaymentPurpose purpose, Long userId,
                              Long customerId, String proposalNumber, String policyNumber, BigDecimal amount, String currency,
                              PaymentMethod paymentMethod, String provider, String providerTxnId, String failureReason,
                              String refundReference, Instant refundedAt, String refundReason, Instant completedAt,
                              Instant createdAt) {
}
