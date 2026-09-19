package com.insurance.payment.service;

import com.insurance.payment.provider.MockPaymentProvider;
import com.insurance.payment.provider.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentProviderTest {

    private final MockPaymentProvider provider = new MockPaymentProvider();

    @Test
    void testInstrumentsBehaveAsDocumented() {
        assertThat(provider.charge(req("4111********1111")).success()).isTrue();
        assertThat(provider.charge(req("jane@upi")).success()).isTrue();
        PaymentProvider.ChargeResult declined = provider.charge(req("4000********0002"));
        assertThat(declined.success()).isFalse();
        assertThat(declined.failureReason()).isEqualTo("Card declined by issuer");
        assertThatThrownBy(() -> provider.charge(req("4000********0009"))).isInstanceOf(IllegalStateException.class);
        assertThat(provider.refund("mock_x", BigDecimal.TEN, "r").success()).isTrue();
    }

    private static PaymentProvider.ChargeRequest req(String instrument) {
        return new PaymentProvider.ChargeRequest("PAY-1", BigDecimal.TEN, "INR", "CARD", instrument);
    }
}
