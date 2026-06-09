package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.refund.repository.RefundRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundAmountCalculatorTest {

    @Mock
    RefundRepository refundRepository;

    @Mock
    Payment payment;

    RefundAmountCalculator refundAmountCalculator;

    @BeforeEach
    void setUp() {
        refundAmountCalculator = new RefundAmountCalculator(
                refundRepository,
                new RefundPointSettlementCalculator()
        );
    }

    @Test
    void 부분환불계산_PG환불가능금액이남아있으면_PG금액을먼저배분한다() {
        // given
        결제금액(1L, 10_000L, 2_000L, 8_000L, 100L);
        완료환불누적(1L, 0L, 0L, 0L);

        // when
        RefundAmount amount = refundAmountCalculator.calculate(
                payment,
                3_000L,
                10_000L,
                1_000L
        );

        // then
        assertThat(amount.requestedRefundAmount()).isEqualTo(3_000L);
        assertThat(amount.grossPgRefundAmount()).isEqualTo(3_000L);
        assertThat(amount.grossPointRefundAmount()).isZero();
        assertThat(amount.earnedPointRecoveryAmount()).isEqualTo(30L);
        assertThat(amount.recoveredFromBalance()).isEqualTo(30L);
        assertThat(amount.deductedFromPgRefund()).isZero();
        assertThat(amount.pointRefundAmount()).isZero();
        assertThat(amount.pgRefundAmount()).isEqualTo(3_000L);
        assertThat(amount.refundAmount()).isEqualTo(3_000L);
    }

    @Test
    void 부분환불계산_PG잔여액이부족하면_부족분만사용포인트반환대상으로배분한다() {
        // given
        결제금액(1L, 10_000L, 3_000L, 7_000L, 100L);
        완료환불누적(1L, 0L, 6_500L, 0L);

        // when
        RefundAmount amount = refundAmountCalculator.calculate(
                payment,
                1_000L,
                3_500L,
                0L
        );

        // then
        assertThat(amount.grossPgRefundAmount()).isEqualTo(500L);
        assertThat(amount.grossPointRefundAmount()).isEqualTo(500L);
        assertThat(amount.earnedPointRecoveryAmount()).isEqualTo(10L);
        assertThat(amount.recoveredFromUsedPoint()).isEqualTo(10L);
        assertThat(amount.pointRefundAmount()).isEqualTo(490L);
        assertThat(amount.pgRefundAmount()).isEqualTo(500L);
        assertThat(amount.refundAmount()).isEqualTo(990L);
    }

    @Test
    void 마지막전체환불계산_남은포인트와PG환불가능금액과적립포인트회수액을모두배분한다() {
        // given
        결제금액(1L, 10_000L, 1_000L, 9_000L, 90L);
        완료환불누적(1L, 0L, 3_000L, 30L);

        // when
        RefundAmount amount = refundAmountCalculator.calculate(
                payment,
                7_000L,
                7_000L,
                0L
        );

        // then
        assertThat(amount.grossPgRefundAmount()).isEqualTo(6_000L);
        assertThat(amount.grossPointRefundAmount()).isEqualTo(1_000L);
        assertThat(amount.earnedPointRecoveryAmount()).isEqualTo(60L);
        assertThat(amount.recoveredFromUsedPoint()).isEqualTo(60L);
        assertThat(amount.pointRefundAmount()).isEqualTo(940L);
        assertThat(amount.pgRefundAmount()).isEqualTo(6_000L);
        assertThat(amount.refundAmount()).isEqualTo(6_940L);
    }

    @Test
    void 환불계산_완료환불누적값이원결제금액보다크면_VALIDATION_FAILED가발생한다() {
        // given
        결제금액(1L, 10_000L, 1_000L, 9_000L, 90L);
        완료환불누적(1L, 2_000L, 0L, 0L);

        // when
        // then
        assertThatThrownBy(() -> refundAmountCalculator.calculate(
                payment,
                1_000L,
                10_000L,
                0L
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private void 결제금액(
            Long paymentId,
            Long totalAmount,
            Long usedPointAmount,
            Long pgAmount,
            Long rewardPointAmount
    ) {
        lenient().when(payment.getId()).thenReturn(paymentId);
        lenient().when(payment.getTotalAmount()).thenReturn(totalAmount);
        lenient().when(payment.getUsedPointAmount()).thenReturn(usedPointAmount);
        lenient().when(payment.getPgAmount()).thenReturn(pgAmount);
        lenient().when(payment.getRewardPointAmount()).thenReturn(rewardPointAmount);
    }

    private void 완료환불누적(
            Long paymentId,
            Long grossPointRefundAmount,
            Long grossPgRefundAmount,
            Long earnedPointRecoveryAmount
    ) {
        when(refundRepository.sumCompletedGrossPointRefundAmount(paymentId)).thenReturn(grossPointRefundAmount);
        when(refundRepository.sumCompletedGrossPgRefundAmount(paymentId)).thenReturn(grossPgRefundAmount);
        when(refundRepository.sumCompletedEarnedPointRecoveryAmount(paymentId)).thenReturn(earnedPointRecoveryAmount);
    }
}
