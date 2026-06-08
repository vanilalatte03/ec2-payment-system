package com.teamec2.paymentsystem.domain.order.facade;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;

public record OrderProductTarget(
        CartItem cartItem,
        Product product
) {
}
