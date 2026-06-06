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
 * @param refundAmount 상품별 총 환불 금액
 * @param pointRefundAmount 상품별 포인트 환불 금액
 * @param pgRefundAmount 상품별 PG 환불 금액
 */
public record RefundItemResponse(
        Long refundItemId,
        Long orderItemId,
        String productName,
        int refundQuantity,
        Long unitPrice,
        Long refundAmount,
        Long pointRefundAmount,
        Long pgRefundAmount
) {
    public static RefundItemResponse from(RefundItem refundItem) {
        return new RefundItemResponse(
                refundItem.getId(),
                refundItem.getOrderItem().getId(),
                refundItem.getOrderItem().getProductName(),
                refundItem.getRefundQuantity(),
                refundItem.getUnitPrice(),
                refundItem.getRefundAmount(),
                refundItem.getPointRefundAmount(),
                refundItem.getPgRefundAmount()
        );
    }
}
