package com.teamec2.paymentsystem.domain.refund.dto;

import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;

/**
 * 환불 상품 상세 응답 DTO입니다.
 *
 * @param refundItemId 환불 상품 상세 ID
 * @param orderItemId 주문 상품 ID
 * @param productName 상품명
 * @param refundQuantity 환불한 상품 수량
 * @param unitPrice 환불 당시 상품 단가
 * @param grossRefundAmount 상품 가격 * 환불 수량 기준 금액입니다. 적립 포인트 회수 전 금액입니다.
 * @param actualRefundAmount 이 상품에 배분된 실제 반환 금액입니다. pointRefundAmount + pgRefundAmount 입니다.
 * @param pointRefundAmount 상품별 포인트 환불 금액
 * @param pgRefundAmount 상품별 PG 환불 금액
 */
public record RefundItemResponse(
        Long refundItemId,
        Long orderItemId,
        String productName,
        int refundQuantity,
        Long unitPrice,
        Long grossRefundAmount,
        Long actualRefundAmount,
        Long pointRefundAmount,
        Long pgRefundAmount
) {
    public static RefundItemResponse from(RefundItem refundItem) {
        Long actualRefundAmount = refundItem.getPointRefundAmount() + refundItem.getPgRefundAmount();
        return new RefundItemResponse(
                refundItem.getId(),
                refundItem.getOrderItem().getId(),
                refundItem.getOrderItem().getProductName(),
                refundItem.getRefundQuantity(),
                refundItem.getUnitPrice(),
                refundItem.getRefundAmount(),
                actualRefundAmount,
                refundItem.getPointRefundAmount(),
                refundItem.getPgRefundAmount()
        );
    }
}
