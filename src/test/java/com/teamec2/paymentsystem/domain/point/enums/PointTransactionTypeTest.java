package com.teamec2.paymentsystem.domain.point.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointTransactionTypeTest {

    @Test
    void 포인트거래타입_결제환불계정타입을구분한다() {
        // given

        // when
        // then
        assertThat(PointTransactionType.USE_RESERVE.isPaymentType()).isTrue();
        assertThat(PointTransactionType.USE.isPaymentType()).isTrue();
        assertThat(PointTransactionType.USE_CANCEL.isPaymentType()).isTrue();
        assertThat(PointTransactionType.EARN.isPaymentType()).isTrue();
        assertThat(PointTransactionType.USE_RESTORE.isPaymentType()).isFalse();

        assertThat(PointTransactionType.USE_RESTORE.isRefundType()).isTrue();
        assertThat(PointTransactionType.EARN_CANCEL.isRefundType()).isTrue();
        assertThat(PointTransactionType.EARN_RECOVERY_RESERVE.isRefundType()).isTrue();
        assertThat(PointTransactionType.EARN_RECOVERY_RELEASE.isRefundType()).isTrue();
        assertThat(PointTransactionType.EARN.isRefundType()).isFalse();

        assertThat(PointTransactionType.SIGNUP_BONUS.isAccountType()).isTrue();
        assertThat(PointTransactionType.EARN.isAccountType()).isFalse();
    }
}
