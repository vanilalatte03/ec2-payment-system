package com.teamec2.paymentsystem.domain.payment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    void 결제상태는_대기_완료_실패_부분환불_전액환불을_가진다() {
        // when
        PaymentStatus[] statuses = PaymentStatus.values();

        // then
        assertThat(statuses).containsExactly(
                PaymentStatus.PENDING,
                PaymentStatus.COMPLETED,
                PaymentStatus.FAILED,
                PaymentStatus.PARTIAL_REFUNDED,
                PaymentStatus.FULL_REFUNDED
        );
    }

    @Test
    void 결제대기상태는_완료또는실패로_변경할수있다() {
        // when
        boolean canTransitionToCompleted = PaymentStatus.PENDING.canTransitionTo(PaymentStatus.COMPLETED);
        boolean canTransitionToFailed = PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED);

        // then
        assertThat(canTransitionToCompleted).isTrue();
        assertThat(canTransitionToFailed).isTrue();
    }

    @Test
    void 결제대기상태는_환불상태로_변경할수없다() {
        // when
        boolean canTransitionToPartialRefunded = PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PARTIAL_REFUNDED);
        boolean canTransitionToRefunded = PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FULL_REFUNDED);

        // then
        assertThat(canTransitionToPartialRefunded).isFalse();
        assertThat(canTransitionToRefunded).isFalse();
    }

    @Test
    void 결제완료상태는_부분환불또는전액환불로_변경할수있다() {
        // when
        boolean canTransitionToPartialRefunded = PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.PARTIAL_REFUNDED);
        boolean canTransitionToRefunded = PaymentStatus.COMPLETED.canTransitionTo(PaymentStatus.FULL_REFUNDED);

        // then
        assertThat(canTransitionToPartialRefunded).isTrue();
        assertThat(canTransitionToRefunded).isTrue();
    }

    @Test
    void 부분환불상태는_전액환불로만_변경할수있다() {
        // when
        boolean canTransitionToRefunded = PaymentStatus.PARTIAL_REFUNDED.canTransitionTo(PaymentStatus.FULL_REFUNDED);
        boolean canTransitionToCompleted = PaymentStatus.PARTIAL_REFUNDED.canTransitionTo(PaymentStatus.COMPLETED);

        // then
        assertThat(canTransitionToRefunded).isTrue();
        assertThat(canTransitionToCompleted).isFalse();
    }

    @Test
    void 실패상태와전액환불상태는_다른상태로_변경할수없다() {
        // when
        boolean failedToCompleted = PaymentStatus.FAILED.canTransitionTo(PaymentStatus.COMPLETED);
        boolean refundedToCompleted = PaymentStatus.FULL_REFUNDED.canTransitionTo(PaymentStatus.COMPLETED);

        // then
        assertThat(failedToCompleted).isFalse();
        assertThat(refundedToCompleted).isFalse();
    }
}
