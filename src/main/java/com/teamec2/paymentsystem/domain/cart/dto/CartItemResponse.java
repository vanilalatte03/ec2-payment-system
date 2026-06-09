package com.teamec2.paymentsystem.domain.cart.dto;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;

public record CartItemResponse (
        Long cartItemId,
        Long productId,
        String productName,
        int quantity,
        int unitPrice,
        Long lineAmount,
        int stock,
        ProductStatus status
) {

    public static CartItemResponse from(CartItem cartItem) {
        Product product = cartItem.getProduct();

        return new CartItemResponse(
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
