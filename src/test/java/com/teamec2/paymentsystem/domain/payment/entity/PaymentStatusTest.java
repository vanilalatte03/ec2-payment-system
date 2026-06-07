package com.teamec2.paymentsystem.domain.payment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusTest {

    @Test
    void 결제상태는_대기_완료_실패_보상정리필요_보상결과미확정_부분환불_전액환불을_가진다() {
        // when
        PaymentStatus[] statuses = PaymentStatus.values();

        // then
        assertThat(statuses).containsExactly(
                PaymentStatus.PENDING,
                PaymentStatus.COMPLETED,
                PaymentStatus.FAILED,
                PaymentStatus.COMPENSATION_REQUIRED,
                PaymentStatus.COMPENSATION_RESULT_UNKNOWN,
                PaymentStatus.PARTIAL_REFUNDED,
                PaymentStatus.FULL_REFUNDED
        );
    }

    @Test
    void 결제대기상태는_완료_실패_보상상태로_변경할수있다() {
        // when
        boolean canTransitionToCompleted = PaymentStatus.PENDING.canTransitionTo(PaymentStatus.COMPLETED);
        boolean canTransitionToFailed = PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED);
        boolean canTransitionToCompensationRequired = PaymentStatus.PENDING
                .canTransitionTo(PaymentStatus.COMPENSATION_REQUIRED);
        boolean canTransitionToCompensationResultUnknown = PaymentStatus.PENDING
                .canTransitionTo(PaymentStatus.COMPENSATION_RESULT_UNKNOWN);

        // then
        assertThat(canTransitionToCompleted).isTrue();
        assertThat(canTransitionToFailed).isTrue();
        assertThat(canTransitionToCompensationRequired).isTrue();
        assertThat(canTransitionToCompensationResultUnknown).isTrue();
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
    void 보상정리필요상태는_실패상태로만_변경할수있다() {
        // when
        boolean canTransitionToFailed = PaymentStatus.COMPENSATION_REQUIRED.canTransitionTo(PaymentStatus.FAILED);
        boolean canTransitionToCompleted = PaymentStatus.COMPENSATION_REQUIRED.canTransitionTo(PaymentStatus.COMPLETED);

        // then
        assertThat(canTransitionToFailed).isTrue();
        assertThat(canTransitionToCompleted).isFalse();
    }

    @Test
    void 보상결과미확정상태는_보상정리필요또는실패상태로_변경할수있다() {
        // when
        boolean canTransitionToCompensationRequired = PaymentStatus.COMPENSATION_RESULT_UNKNOWN
                .canTransitionTo(PaymentStatus.COMPENSATION_REQUIRED);
        boolean canTransitionToFailed = PaymentStatus.COMPENSATION_RESULT_UNKNOWN.canTransitionTo(PaymentStatus.FAILED);
        boolean canTransitionToCompleted = PaymentStatus.COMPENSATION_RESULT_UNKNOWN
                .canTransitionTo(PaymentStatus.COMPLETED);

        // then
        assertThat(canTransitionToCompensationRequired).isTrue();
        assertThat(canTransitionToFailed).isTrue();
        assertThat(canTransitionToCompleted).isFalse();
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
