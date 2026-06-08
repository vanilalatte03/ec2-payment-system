package com.teamec2.paymentsystem.domain.order.dto;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;

import java.util.List;

public record OrderPreviewResponse(
        List<OrderPreviewItemResponse> items,
        int totalQuantity,
        Long totalAmount
) {

    public static OrderPreviewResponse from(List<CartItem> cartItems) {
        List<OrderPreviewItemResponse> itemResponses = cartItems.stream()
                .map(OrderPreviewItemResponse::from)
                .toList();

        int totalQuantity = itemResponses.stream()
                .mapToInt(OrderPreviewItemResponse::quantity)
                .sum();

        Long totalAmount = itemResponses.stream()
                .mapToLong(OrderPreviewItemResponse::lineAmount)
                .sum();

        return new OrderPreviewResponse(itemResponses, totalQuantity, totalAmount);
    }

    public record OrderPreviewItemResponse(
            Long cartItemId,
            Long productId,
            String productName,
            int quantity,
            int unitPrice,
            Long lineAmount,
            int stock,
            ProductStatus status
    ) {

        public static OrderPreviewItemResponse from(CartItem cartItem) {
            Product product = cartItem.getProduct();

            return new OrderPreviewItemResponse(
                    cartItem.getId(),
                    product.getId(),
                    product.getName(),
                    cartItem.getQuantity(),
                    product.getPrice(),
                    (long) product.getPrice() * cartItem.getQuantity(),
                    product.getStock(),
                    product.getStatus()
            );
        }
    }
}
