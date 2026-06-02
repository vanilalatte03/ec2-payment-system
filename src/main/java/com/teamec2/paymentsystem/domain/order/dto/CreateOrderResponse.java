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
public record CreateOrderResponse (
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        Long paymentId,
        String portonePaymentId,
        PaymentStatus paymentStatus,
        PaymentType paymentType,
        Long totalAmount,
        Long usePointAmount,
        Long pgAmount,
        String nextAction,
        List<CreateOrderItemResponse> items
) {
}
