package com.teamec2.paymentsystem.domain.order.dto;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
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

    private static final String CONFIRM_POINT_ONLY = "CONFIRM_POINT_ONLY";
    private static final String OPEN_PORTONE_PAYMENT = "OPEN_PORTONE_PAYMENT";
    private static final String SUCCESS_MESSAGE = "주문과 결제 정보가 생성되었습니다.";

    /**
     * 저장된 주문, 결제, 주문 상품 정보를 주문 생성 응답 DTO로 변환합니다.
     *
     * @param order 생성된 주문
     * @param payment 주문과 함께 생성된 결제 대기 정보
     * @param orderItems 주문에 포함된 상품 목록
     * @return 주문 생성 응답 DTO
     */
    public static CreateOrderResponse from(Order order, Payment payment, List<OrderItem> orderItems) {
        List<CreateOrderItemResponse> itemResponses = orderItems.stream()
                .map(CreateOrderItemResponse::from)
                .toList();

        String nextAction = payment.getPgAmount() == 0
                ? CONFIRM_POINT_ONLY
                : OPEN_PORTONE_PAYMENT;

        return new CreateOrderResponse(
                new OrderData(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getStatus(),
                        order.getTotalAmount(),
                        itemResponses
                ),
                new PaymentData(
                        payment.getId(),
                        payment.getPortonePaymentId(),
                        payment.getStatus(),
                        payment.getPaymentType(),
                        payment.getUsedPointAmount(),
                        payment.getPgAmount()
                ),
                nextAction,
                SUCCESS_MESSAGE
        );
    }

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
            Long usedPointAmount,
            Long pgAmount
    ) {
    }
}
