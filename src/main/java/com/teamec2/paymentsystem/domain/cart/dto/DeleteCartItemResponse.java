package com.teamec2.paymentsystem.domain.cart.dto;

public record DeleteCartItemResponse(
        boolean deleted,
        Long cartItemId,
        Long cartTotalAmount
) {
}
