package com.teamec2.paymentsystem.domain.order.entity;

import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {
    @Test
    @DisplayName("결제대기 주문은 회원 직접 취소로 취소 상태가 될 수 있다")
    void cancelPendingPayment() {
        Order order = Order.create(createUser(), "ORDER-001", 1000, 0);

        order.cancelPendingPayment();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("결제완료 주문은 회원 직접 취소로 취소 상태가 될 수 없다")
    void cancelPendingPaymentAfterCompletedThrowsException() {
        Order order = Order.create(createUser(), "ORDER-001", 1000, 0);
        order.complete();

        assertThatThrownBy(order::cancelPendingPayment)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
    }

    @Test
    @DisplayName("취소된 주문은 결제완료 상태가 될 수 없다")
    void completeAfterCanceledThrowsException() {
        Order order = Order.create(createUser(), "ORDER-001", 1000, 0);
        order.cancelPendingPayment();

        assertThatThrownBy(order::complete)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("결제완료 주문은 전액 환불 처리로 취소 상태가 될 수 있다")
    void cancelCompletedByRefund() {
        Order order = Order.create(createUser(), "ORDER-001", 1000, 0);
        order.complete();

        order.cancelCompletedByRefund();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("결제완료 상태가 아니면 전액 환불 취소 처리할 수 없다")
    void cancelCompletedByRefundBeforeCompletedThrowsException() {
        Order order = Order.create(createUser(), "ORDER-001", 1000, 0);

        assertThatThrownBy(order::cancelCompletedByRefund)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
    }

    @Test
    @DisplayName("주문 생성 시 회원은 필수다")
    void createOrderWithoutUserThrowsException() {
        assertThatThrownBy(() -> Order.create(null, "ORDER-001", 1000, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문번호는 필수다")
    void createOrderWithoutOrderNumberThrowsException() {
        assertThatThrownBy(() -> Order.create(createUser(), " ", 1000, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    @DisplayName("사용 포인트는 0 이상이어야 한다")
    void createOrderWithNegativeUsedPointThrowsException() {
        assertThatThrownBy(() -> Order.create(createUser(), "ORDER-001", 1000, -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_USED_POINT);
    }

    @Test
    @DisplayName("사용 포인트는 주문 금액 이하여야 한다")
    void createOrderWithUsedPointGreaterThanTotalAmountThrowsException() {
        assertThatThrownBy(() -> Order.create(createUser(), "ORDER-001", 1000, 1001))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_USED_POINT);
    }

    private User createUser() {
        return User.create(
                "test@example.com",
                "password",
                "테스트유저",
                "010-1234-5678"
        );
    }
}
