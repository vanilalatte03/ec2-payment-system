package com.teamec2.paymentsystem.domain.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateCartItemQuantityRequest (
        @NotNull(message = "주문 수량은 필수로 입력해야 합니다.")
        @Min(value = 1, message = "주문 수량은 1 이상이어야 합니다.")
        Integer quantity
) {
}
