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
        boolean canTransition = OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.COMPLETED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    void 결제완료상태는_결제완료처리할수없다() {
        // when
        boolean canTransition = OrderStatus.COMPLETED.canTransitionTo(OrderStatus.COMPLETED);

        // then
        assertThat(canTransition).isFalse();
    }

    @Test
    void 결제대기상태는_회원직접취소할수있다() {
        // when
        boolean canTransition = OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    void 결제완료상태는_취소상태로_변경할수있다() {
        // when
        boolean canTransition = OrderStatus.COMPLETED.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    void 결제대기상태는_취소상태로_변경할수있다() {
        // when
        boolean canTransition = OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    void 결제대기상태는_결제대기로_변경할수없다() {
        // when
        boolean canTransition = OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PAYMENT_PENDING);

        // then
        assertThat(canTransition).isFalse();
    }

    @Test
    void 취소상태는_다른상태로_변경할수없다() {
        // when
        boolean canTransitionToPaymentPending = OrderStatus.CANCELED.canTransitionTo(OrderStatus.PAYMENT_PENDING);
        boolean canTransitionToCompleted = OrderStatus.CANCELED.canTransitionTo(OrderStatus.COMPLETED);
        boolean canTransitionToCanceled = OrderStatus.CANCELED.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransitionToPaymentPending).isFalse();
        assertThat(canTransitionToCompleted).isFalse();
        assertThat(canTransitionToCanceled).isFalse();
    }
}
