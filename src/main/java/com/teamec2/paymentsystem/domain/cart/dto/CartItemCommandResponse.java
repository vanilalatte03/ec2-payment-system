package com.teamec2.paymentsystem.domain.cart.dto;

public record CartItemCommandResponse(
        Long cartItemId,
        Long productId,
        String productName,
        int quantity,
        int unitPrice,
        Long lineAmount,
        Long cartTotalAmount
) {
}
