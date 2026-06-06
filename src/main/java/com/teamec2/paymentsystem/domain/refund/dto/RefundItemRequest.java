package com.teamec2.paymentsystem.domain.refund.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 부분 환불할 주문 상품과 수량을 나타내는 요청 DTO입니다.
 */
public record RefundItemRequest(

        @NotNull(message = "주문 상품 ID는 필수입니다.")
        Long orderItemId,

        @NotNull(message = "환불 수량은 필수입니다.")
        @Min(value = 1, message = "환불 수량은 1개 이상이어야 합니다.")
        Integer quantity
) {
}


