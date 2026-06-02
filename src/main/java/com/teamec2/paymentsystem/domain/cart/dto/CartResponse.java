package com.teamec2.paymentsystem.domain.cart.dto;

import java.util.List;

public record CartResponse (
        Long cartId,
        List<CartItemResponse> items,
        int totalQuantity,
        Long totalAmount
) {
}
