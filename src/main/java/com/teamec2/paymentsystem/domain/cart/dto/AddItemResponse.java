package com.teamec2.paymentsystem.domain.cart.dto;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;

public record AddItemResponse (
        Long cartItemId,
        Long productId,
        String productName,
        int quantity,
        int unitPrice,
        Long lineAmount,
        Long cartTotalAmount
) {

    public static AddItemResponse from(CartItem cartItem, Long cartTotalAmount) {
        Product product = cartItem.getProduct();
        long lineAmount = (long) product.getPrice() * cartItem.getQuantity();

        return new AddItemResponse(
                cartItem.getId(),
                product.getId(),
                product.getName(),
                cartItem.getQuantity(),
                product.getPrice(),
                lineAmount,
                cartTotalAmount
        );
    }
}
