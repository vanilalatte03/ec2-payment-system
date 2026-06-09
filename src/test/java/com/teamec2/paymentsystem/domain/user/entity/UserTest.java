package com.teamec2.paymentsystem.domain.user.entity;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void 포인트잔액증가_양수이면_잔액을증가한다() {
        // given
        User user = User.create("test@example.com", "Password123!", "홍길동", "010-1234-5678");

        // when
        user.increasePointBalance(1_000L);

        // then
        assertThat(user.getPointBalance()).isEqualTo(1_000L);
    }

    @Test
    void 포인트잔액증가_null_0_음수이면_POINT_INCREASE_AMOUNT_INVALID가발생한다() {
        // given
        User user = User.create("test@example.com", "Password123!", "홍길동", "010-1234-5678");

        // when
        // then
        assertThatThrownBy(() -> user.increasePointBalance(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_INCREASE_AMOUNT_INVALID);
        assertThatThrownBy(() -> user.increasePointBalance(0L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_INCREASE_AMOUNT_INVALID);
        assertThatThrownBy(() -> user.increasePointBalance(-1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_INCREASE_AMOUNT_INVALID);
    }

    @Test
    void 포인트잔액감소_잔액이충분하면_잔액을감소한다() {
        // given
        User user = User.create("test@example.com", "Password123!", "홍길동", "010-1234-5678");
        user.increasePointBalance(1_000L);

        // when
        user.decreasePointBalance(300L);

        // then
        assertThat(user.getPointBalance()).isEqualTo(700L);
    }

    @Test
    void 포인트잔액감소_null_0_음수이면_POINT_DECREASE_AMOUNT_INVALID가발생한다() {
        // given
        User user = User.create("test@example.com", "Password123!", "홍길동", "010-1234-5678");
        user.increasePointBalance(1_000L);

        // when
        // then
        assertThatThrownBy(() -> user.decreasePointBalance(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_DECREASE_AMOUNT_INVALID);
        assertThatThrownBy(() -> user.decreasePointBalance(0L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_DECREASE_AMOUNT_INVALID);
        assertThatThrownBy(() -> user.decreasePointBalance(-1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POINT_DECREASE_AMOUNT_INVALID);
    }

    @Test
    void 포인트잔액감소_잔액부족이면_INSUFFICIENT_POINT가발생한다() {
        // given
        User user = User.create("test@example.com", "Password123!", "홍길동", "010-1234-5678");
        user.increasePointBalance(100L);

        // when
        // then
        assertThatThrownBy(() -> user.decreasePointBalance(101L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_POINT);
        assertThat(user.getPointBalance()).isEqualTo(100L);
    }
}
