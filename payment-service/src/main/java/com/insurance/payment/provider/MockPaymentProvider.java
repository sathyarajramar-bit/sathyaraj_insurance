package com.insurance.payment.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deterministic fake gateway so flows can be exercised without money: an instrument ending in
 * 0002 is declined, 0009 raises a gateway error, everything else succeeds (the "test card" convention).
 */
@Slf4j
@Component
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String instrument = request.instrument() == null ? "" : request.instrument();
        if (instrument.endsWith("0009")) {
            throw new IllegalStateException("Mock gateway timeout");
        }
        if (instrument.endsWith("0002")) {
            log.info("MOCK charge {} declined", request.paymentReference());
            return new ChargeResult(false, null, "Card declined by issuer");
        }
        String txn = "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        log.info("MOCK charge {} of {} {} approved, txn {}", request.paymentReference(), request.amount(), request.currency(), txn);
        return new ChargeResult(true, txn, null);
    }

    @Override
    public RefundResult refund(String providerTxnId, BigDecimal amount, String reason) {
        log.info("MOCK refund of {} for txn {} ({})", amount, providerTxnId, reason);
        return new RefundResult(true, "rfnd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20), null);
    }
}
