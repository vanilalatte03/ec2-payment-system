package com.teamec2.paymentsystem.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * 주문 생성 요청 DTO입니다.
 *
 * cartItemIds가 비어 있거나 null이면 장바구니 전체 상품을 주문 대상으로 봅니다.
 * usedPointAmount는 주문생성 시 포인트가 예약 차감됩니다.
 */
public record CreateOrderRequest(
        List<Long> cartItemIds,

        @NotNull
        @PositiveOrZero
        Long usedPointAmount
) {
}
