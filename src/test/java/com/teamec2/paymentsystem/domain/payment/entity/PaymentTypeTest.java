package com.teamec2.paymentsystem.domain.payment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTypeTest {

    @Test
    void PG금액만있으면_CARD를반환한다() {
        // when
        PaymentType paymentType = PaymentType.from(0L, 1000L);

        // then
        assertThat(paymentType).isEqualTo(PaymentType.CARD);
    }

    @Test
    void 포인트금액만있으면_POINT_ONLY를반환한다() {
        // when
        PaymentType paymentType = PaymentType.from(1000L, 0L);

        // then
        assertThat(paymentType).isEqualTo(PaymentType.POINT_ONLY);
    }

    @Test
    void 포인트와PG금액이모두있으면_POINT_CARD를반환한다() {
        // when
        PaymentType paymentType = PaymentType.from(500L, 1000L);

        // then
        assertThat(paymentType).isEqualTo(PaymentType.POINT_CARD);
    }
}
