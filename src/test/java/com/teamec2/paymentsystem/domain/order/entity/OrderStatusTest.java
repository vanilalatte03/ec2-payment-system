package com.teamec2.paymentsystem.domain.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void 주문상태는_결제대기_결제완료_취소를_가진다() {
        // when
        OrderStatus[] statuses = OrderStatus.values();

        // then
        assertThat(statuses).containsExactly(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.COMPLETED,
                OrderStatus.CANCELED
        );
    }

    @Test
    void 결제대기상태는_결제완료처리할수있다() {
        // when
        boolean canCompletePayment = OrderStatus.PAYMENT_PENDING.canCompletePayment();

        // then
        assertThat(canCompletePayment).isTrue();
    }

    @Test
    void 결제완료상태는_결제완료처리할수없다() {
        // when
        boolean canCompletePayment = OrderStatus.COMPLETED.canCompletePayment();

        // then
        assertThat(canCompletePayment).isFalse();
    }

    @Test
    void 결제대기상태는_회원직접취소할수있다() {
        // when
        boolean canCancel = OrderStatus.PAYMENT_PENDING.canCancelPendingPayment();

        // then
        assertThat(canCancel).isTrue();
    }

    @Test
    void 결제완료상태는_회원직접취소할수없다() {
        // when
        boolean canCancel = OrderStatus.COMPLETED.canCancelPendingPayment();

        // then
        assertThat(canCancel).isFalse();
    }

    @Test
    void 결제완료상태는_전액환불로_취소할수있다() {
        // when
        boolean canCancel = OrderStatus.COMPLETED.canCancelCompletedByRefund();

        // then
        assertThat(canCancel).isTrue();
    }

    @Test
    void 결제대기상태는_전액환불로_취소할수없다() {
        // when
        boolean canCancel = OrderStatus.PAYMENT_PENDING.canCancelCompletedByRefund();

        // then
        assertThat(canCancel).isFalse();
    }

    @Test
    void 취소상태는_목적별_상태변경을_할수없다() {
        // when
        boolean canCompletePayment = OrderStatus.CANCELED.canCompletePayment();
        boolean canCancelPendingPayment = OrderStatus.CANCELED.canCancelPendingPayment();
        boolean canCancelCompletedByRefund = OrderStatus.CANCELED.canCancelCompletedByRefund();

        // then
        assertThat(canCompletePayment).isFalse();
        assertThat(canCancelPendingPayment).isFalse();
        assertThat(canCancelCompletedByRefund).isFalse();
    }
}
