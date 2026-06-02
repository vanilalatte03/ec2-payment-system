package com.teamec2.paymentsystem.domain.cart.dto;

import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        String productName,
        int quantity,
        int unitPrice,
        Long lineAmount,
        int stock,
        ProductStatus status
) {
}
