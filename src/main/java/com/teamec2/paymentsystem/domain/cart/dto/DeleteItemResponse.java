package com.teamec2.paymentsystem.domain.cart.dto;

public record DeleteItemResponse (
        boolean deleted,
        Long cartItemId,
        Long cartTotalAmount
) {
}
