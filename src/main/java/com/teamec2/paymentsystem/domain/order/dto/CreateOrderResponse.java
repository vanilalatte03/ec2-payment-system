package com.teamec2.paymentsystem.domain.order.dto;

import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;

import java.util.List;

/**
 * 주문 생성 결과 DTO입니다.
 *
 * 클라이언트는 pgAmount와 nextAction을 보고 PortOne 결제창을 열지,
 * 포인트 전액 결제 확정 API로 바로 넘어갈지 판단할 수 있습니다.
 */
public record CreateOrderResponse(
        OrderData order,
        PaymentData payment,
        String nextAction,
        String message
) {

    public record OrderData(
            Long orderId,
            String orderNumber,
            OrderStatus status,
            Long totalAmount,
            List<CreateOrderItemResponse> items
    ) {
    }

    public record PaymentData(
            Long paymentId,
            String portonePaymentId,
            PaymentStatus status,
            PaymentType type,
            Long usePointAmount,
            Long pgAmount
    ) {
    }
}