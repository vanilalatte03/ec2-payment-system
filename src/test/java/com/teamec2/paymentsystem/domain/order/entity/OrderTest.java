package com.teamec2.paymentsystem.domain.order.entity;

import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void 결제대기주문_회원이직접취소하면_취소상태가된다() {
        // given
        Order order = Order.create(회원_생성(), "ORDER-001", 1000, 0);

        // when
        order.cancelPendingPayment();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void 결제완료주문_회원이직접취소하면_ORDER_CANCEL_NOT_ALLOWED가발생한다() {
        // given
        Order order = Order.create(회원_생성(), "ORDER-001", 1000, 0);
        order.complete();

        // when
        // then
        assertThatThrownBy(order::cancelPendingPayment)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
    }

    @Test
    void 취소된주문_결제완료처리하면_INVALID_ORDER_STATUS가발생한다() {
        // given
        Order order = Order.create(회원_생성(), "ORDER-001", 1000, 0);
        order.cancelPendingPayment();

        // when
        // then
        assertThatThrownBy(order::complete)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    void 결제완료주문_전액환불처리하면_취소상태가된다() {
        // given
        Order order = Order.create(회원_생성(), "ORDER-001", 1000, 0);
        order.complete();

        // when
        order.cancelCompletedByRefund();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void 결제완료상태가아닌주문_전액환불취소처리하면_REFUND_NOT_ALLOWED가발생한다() {
        // given
        Order order = Order.create(회원_생성(), "ORDER-001", 1000, 0);

        // when
        // then
        assertThatThrownBy(order::cancelCompletedByRefund)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
    }

    @Test
    void 주문생성_회원이없으면_USER_NOT_FOUND가발생한다() {
        // given

        // when
        // then
        assertThatThrownBy(() -> Order.create(null, "ORDER-001", 1000, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 주문생성_주문번호가비어있으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        User user = 회원_생성();

        // when
        // then
        assertThatThrownBy(() -> Order.create(user, " ", 1000, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 주문생성_사용포인트가음수면_INVALID_USED_POINT가발생한다() {
        // given
        User user = 회원_생성();

        // when
        // then
        assertThatThrownBy(() -> Order.create(user, "ORDER-001", 1000, -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_USED_POINT);
    }

    @Test
    void 주문생성_사용포인트가주문금액보다크면_INVALID_USED_POINT가발생한다() {
        // given
        User user = 회원_생성();

        // when
        // then
        assertThatThrownBy(() -> Order.create(user, "ORDER-001", 1000, 1001))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_USED_POINT);
    }

    private User 회원_생성() {
        return User.create(
                "test@example.com",
                "password",
                "테스트유저",
                "010-1234-5678"
        );
    }
}
