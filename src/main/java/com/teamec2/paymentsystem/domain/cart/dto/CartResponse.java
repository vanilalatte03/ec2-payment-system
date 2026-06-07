package com.teamec2.paymentsystem.domain.cart.dto;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;

import java.util.List;

public record CartResponse (
        Long cartId,
        List<CartItemResponse> items,
        int totalQuantity,
        Long totalAmount
) {

    public static CartResponse from(Cart cart, List<CartItem> cartItems) {
        List<CartItemResponse> items = cartItems.stream()
                .map(CartItemResponse::from)
                .toList();

        int totalQuantity = items.stream()
                .mapToInt(CartItemResponse::quantity)
                .sum();

        Long totalAmount = items.stream()
                .mapToLong(CartItemResponse::lineAmount)
                .sum();

        return new CartResponse(cart.getId(), items, totalQuantity, totalAmount);
    }

    public static CartResponse empty() {
        return new CartResponse(null, List.of(), 0, 0L);
    }
}
