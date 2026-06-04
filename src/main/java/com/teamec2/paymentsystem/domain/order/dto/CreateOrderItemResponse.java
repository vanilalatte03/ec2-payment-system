package com.teamec2.paymentsystem.domain.order.dto;

import com.teamec2.paymentsystem.domain.order.entity.OrderItem;

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
        int refundedQuantity,
        int unitPrice,
        Long lineAmount
) {

    /**
     * 주문 상품 엔티티를 주문 상품 응답 DTO로 변환합니다.
     *
     * @param orderItem 주문 상품 엔티티
     * @return 주문 상품 응답 DTO
     */
    public static CreateOrderItemResponse from(OrderItem orderItem) {
        return new CreateOrderItemResponse(
                orderItem.getId(),
                orderItem.getProductId(),
                orderItem.getProductName(),
                orderItem.getQuantity(),
                orderItem.getRefundedQuantity(),
                orderItem.getPrice(),
                orderItem.getSubtotal()
        );
    }
}
