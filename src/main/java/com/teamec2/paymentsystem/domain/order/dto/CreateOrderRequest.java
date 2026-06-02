package com.teamec2.paymentsystem.domain.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * 주문 생성 요청 DTO입니다.
 *
 * cartItemIds가 비어 있거나 null이면 장바구니 전체 상품을 주문 대상으로 봅니다.
 * usePointAmount는 주문에 사용할 포인트 금액이며, 실제 포인트 차감은 결제 완료 흐름에서 처리하는 것이 안전합니다.
 */
public record CreateOrderRequest(
        List<Long> cartItemIds,

        @NotNull
        @PositiveOrZero
        Long usePointAmount
) {
}
