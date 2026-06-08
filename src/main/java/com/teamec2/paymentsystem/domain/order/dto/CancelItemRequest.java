package com.teamec2.paymentsystem.domain.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CancelItemRequest(
        @NotNull(message = "주문 상품 ID는 필수입니다.")
        Long orderItemId,

        @NotNull(message = "취소 수량은 필수입니다.")
        @Min(value = 1, message = "취소 수량은 1개 이상이어야 합니다.")
        Integer quantity
) {
}
