package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundPointSettlementCalculatorTest {

    RefundPointSettlementCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new RefundPointSettlementCalculator();
    }

    @Test
    void 포인트정산_적립포인트회수는_반환예정사용포인트_보유포인트_PG환불금액순서로처리한다() {
        // given

        // when
        RefundPointSettlementCalculator.RefundPointSettlement settlement = calculator.calculate(
                100L,
                500L,
                180L,
                50L
        );

        // then
        assertThat(settlement.grossPointRefundAmount()).isEqualTo(100L);
        assertThat(settlement.grossPgRefundAmount()).isEqualTo(500L);
        assertThat(settlement.earnedPointRecoveryAmount()).isEqualTo(180L);
        assertThat(settlement.recoveredFromUsedPoint()).isEqualTo(100L);
        assertThat(settlement.recoveredFromBalance()).isEqualTo(50L);
        assertThat(settlement.deductedFromPgRefund()).isEqualTo(30L);
        assertThat(settlement.pointRefundAmount()).isZero();
        assertThat(settlement.pgRefundAmount()).isEqualTo(470L);
    }

    @Test
    void 포인트정산_회수대상이사용포인트보다작으면_사용포인트에서만상계한다() {
        // given

        // when
        RefundPointSettlementCalculator.RefundPointSettlement settlement = calculator.calculate(
                300L,
                700L,
                120L,
                0L
        );

        // then
        assertThat(settlement.recoveredFromUsedPoint()).isEqualTo(120L);
        assertThat(settlement.recoveredFromBalance()).isZero();
        assertThat(settlement.deductedFromPgRefund()).isZero();
        assertThat(settlement.pointRefundAmount()).isEqualTo(180L);
        assertThat(settlement.pgRefundAmount()).isEqualTo(700L);
    }

    @Test
    void 포인트정산_음수금액이면_VALIDATION_FAILED가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> calculator.calculate(-1L, 500L, 100L, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 포인트정산_회수부족분이PG환불예정액보다크면_VALIDATION_FAILED가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> calculator.calculate(0L, 100L, 150L, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
