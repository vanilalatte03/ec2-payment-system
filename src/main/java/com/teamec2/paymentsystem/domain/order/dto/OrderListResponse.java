package com.teamec2.paymentsystem.domain.order.dto;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderListResponse(
        List<OrderSummaryResponse> orders
) {

    public static OrderListResponse from(List<Order> orders) {
        List<OrderSummaryResponse> orderResponses = orders.stream()
                .map(OrderSummaryResponse::from)
                .toList();

        return new OrderListResponse(orderResponses);
    }

    public record OrderSummaryResponse(
            Long orderId,
            String orderNumber,
            OrderStatus status,
            Long totalAmount,
            LocalDateTime orderedAt
    ) {

        public static OrderSummaryResponse from(Order order) {
            return new OrderSummaryResponse(
                    order.getId(),
                    order.getOrderNumber(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getCreatedAt()
            );
        }
    }
}
