package com.insurance.payment.provider;

import java.math.BigDecimal;

/**
 * Port to the payment gateway. The service only knows this interface; {@link MockPaymentProvider} is the
 * development adapter, a Razorpay/Stripe adapter would implement the same three calls. Real gateways are
 * asynchronous (redirect + webhook); such an adapter would return INITIATED and a webhook controller would
 * complete the payment through the same service path.
 */
public interface PaymentProvider {

    String name();

    ChargeResult charge(ChargeRequest request);

    RefundResult refund(String providerTxnId, BigDecimal amount, String reason);

    record ChargeRequest(String paymentReference, BigDecimal amount, String currency, String method, String instrument) {
    }

    record ChargeResult(boolean success, String providerTxnId, String failureReason) {
    }

    record RefundResult(boolean success, String refundReference, String failureReason) {
    }
}
