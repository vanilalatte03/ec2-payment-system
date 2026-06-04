package com.teamec2.paymentsystem.domain.point.service;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointPolicyTest {

    private final PointPolicy pointPolicy = new PointPolicy();

    @Test
    void 적립포인트계산_PG결제금액의1퍼센트를반환한다() {
        // given
        Long pgAmount = 73000L;

        // when
        Long rewardPoint = pointPolicy.calculateRewardPoint(pgAmount);

        // then
        assertThat(rewardPoint).isEqualTo(730L);
    }

    @Test
    void 적립포인트계산_100원미만이면_0포인트를반환한다() {
        // given
        Long pgAmount = 99L;

        // when
        Long rewardPoint = pointPolicy.calculateRewardPoint(pgAmount);

        // then
        assertThat(rewardPoint).isZero();
    }

    @Test
    void 적립포인트계산_0원이면_0포인트를반환한다() {
        // given
        Long pgAmount = 0L;

        // when
        Long rewardPoint = pointPolicy.calculateRewardPoint(pgAmount);

        // then
        assertThat(rewardPoint).isZero();
    }

    @Test
    void 적립포인트계산_금액이null이면_VALIDATION_FAILED가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> pointPolicy.calculateRewardPoint(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 적립포인트계산_금액이음수이면_VALIDATION_FAILED가발생한다() {
        // when
        // then
        assertThatThrownBy(() -> pointPolicy.calculateRewardPoint(-1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
