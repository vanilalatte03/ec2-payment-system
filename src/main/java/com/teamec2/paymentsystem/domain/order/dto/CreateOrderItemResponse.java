package com.teamec2.paymentsystem.domain.order.dto;

/**
 * 주문 상품 응답 DTO입니다.
 *
 * productName과 unitPrice는 주문 생성 시점의 상품명, 가격 스냅샷입니다.
 * 이후 상품 정보가 바뀌어도 주문 당시의 값을 보존하기 위해 별도로 내려줍니다.
 */
public record CreateOrderItemResponse (
        Long orderItemId,
        Long productId,
        String productName,
        int quantity,
        int unitPrice,
        Long lineAmount
) {
}
