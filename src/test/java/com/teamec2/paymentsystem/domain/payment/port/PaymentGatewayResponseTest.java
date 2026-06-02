package com.teamec2.paymentsystem.domain.payment.port;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayResponseTest {

    @Test
    void 상태가_PAID이면_isPaid가_true를반환한다() {
        // given
        PaymentGatewayResponse response = 결제게이트웨이_응답("pay_123", "PAID", 1000L);

        // when
        boolean paid = response.isPaid();

        // then
        assertThat(paid).isTrue();
    }

    @Test
    void 상태가_PAID가아니면_isPaid가_false를반환한다() {
        // given
        PaymentGatewayResponse response = 결제게이트웨이_응답("pay_123", "FAILED", 1000L);

        // when
        boolean paid = response.isPaid();

        // then
        assertThat(paid).isFalse();
    }

    @Test
    void 결제금액이_같으면_hasSameAmount가_true를반환한다() {
        // given
        PaymentGatewayResponse response = 결제게이트웨이_응답("pay_123", "PAID", 1000L);

        // when
        boolean sameAmount = response.hasSameAmount(1000L);

        // then
        assertThat(sameAmount).isTrue();
    }

    @Test
    void 결제금액이_다르면_hasSameAmount가_false를반환한다() {
        // given
        PaymentGatewayResponse response = 결제게이트웨이_응답("pay_123", "PAID", 1000L);

        // when
        boolean sameAmount = response.hasSameAmount(900L);

        // then
        assertThat(sameAmount).isFalse();
    }

    @Test
    void 결제ID가_같으면_hasSamePaymentId가_true를반환한다() {
        // given
        PaymentGatewayResponse response = 결제게이트웨이_응답("pay_123", "PAID", 1000L);

        // when
        boolean samePaymentId = response.hasSamePaymentId("pay_123");

        // then
        assertThat(samePaymentId).isTrue();
    }

    @Test
    void 결제ID가_다르면_hasSamePaymentId가_false를반환한다() {
        // given
        PaymentGatewayResponse response = 결제게이트웨이_응답("pay_123", "PAID", 1000L);

        // when
        boolean samePaymentId = response.hasSamePaymentId("pay_456");

        // then
        assertThat(samePaymentId).isFalse();
    }

    private PaymentGatewayResponse 결제게이트웨이_응답(String paymentId, String status, Long paidAmount) {
        return new PaymentGatewayResponse(paymentId, status, paidAmount, LocalDateTime.of(2026, 6, 1, 18, 35));
    }
}
