package com.teamec2.paymentsystem.domain.order.dto;

import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record CancelOrderResponse(
        Long orderId,
        String orderNumber,
        OrderStatus previousOrderStatus,
        OrderStatus currentOrderStatus,
        Long canceledAmount,
        Long remainingTotalAmount,
        Long restoredPointAmount,
        Long remainingUsedPointAmount,
        Long remainingPgAmount,
        PaymentStatus paymentStatus,
        List<RestoredStockItem> restoredStockItems,
        LocalDateTime canceledAt
) {
    public record RestoredStockItem(
            Long orderItemId,
            Long productId,
            int restoreQuantity
    ) {
    }
}
