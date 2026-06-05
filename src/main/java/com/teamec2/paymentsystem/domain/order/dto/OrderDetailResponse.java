package com.teamec2.paymentsystem.domain.order.dto;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderItemStatus;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        OrderData order,
        List<OrderItemData> items,
        PaymentData payment,
        PointSummary pointSummary
) {

    public static OrderDetailResponse from(Order order, List<OrderItem> orderItems, Payment payment) {
        List<OrderItemData> itemResponses = orderItems.stream()
                .map(OrderItemData::from)
                .toList();

        return new OrderDetailResponse(
                OrderData.from(order),
                itemResponses,
                PaymentData.from(payment),
                PointSummary.from(payment)
        );
    }

    public record OrderData(
            Long orderId,
            String orderNumber,
            OrderStatus status,
            Long totalAmount,
            Long usedPointAmount,
            LocalDateTime orderedAt
    ) {

        public static OrderData from(Order order) {
            return new OrderData(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getUsedPointAmount(),
                    order.getCreatedAt()
            );
        }
    }

    public record OrderItemData(
            Long orderItemId,
            Long productId,
            String productName,
            int quantity,
            int refundedQuantity,
            OrderItemStatus status,
            int unitPrice,
            Long lineAmount
    ) {

        public static OrderItemData from(OrderItem orderItem) {
            return new OrderItemData(
                    orderItem.getId(),
                    orderItem.getProductId(),
                    orderItem.getProductName(),
                    orderItem.getQuantity(),
                    orderItem.getRefundedQuantity(),
                    orderItem.getStatus(),
                    orderItem.getPrice(),
                    orderItem.getSubtotal()
            );
        }
    }

    public record PaymentData(
            Long paymentId,
            String portonePaymentId,
            PaymentStatus status,
            PaymentType type,
            Long totalAmount,
            Long usedPointAmount,
            Long pgAmount,
            Long rewardPointAmount,
            LocalDateTime approvedAt,
            LocalDateTime failedAt
    ) {

        public static PaymentData from(Payment payment) {
            return new PaymentData(
                    payment.getId(),
                    payment.getPortonePaymentId(),
                    payment.getStatus(),
                    payment.getPaymentType(),
                    payment.getTotalAmount(),
                    payment.getUsedPointAmount(),
                    payment.getPgAmount(),
                    payment.getRewardPointAmount(),
                    payment.getApprovedAt(),
                    payment.getFailedAt()
            );
        }
    }

    public record PointSummary(
            Long usedPointAmount,
            Long rewardPointAmount
    ) {

        public static PointSummary from(Payment payment) {
            return new PointSummary(
                    payment.getUsedPointAmount(),
                    payment.getRewardPointAmount()
            );
        }
    }
}
