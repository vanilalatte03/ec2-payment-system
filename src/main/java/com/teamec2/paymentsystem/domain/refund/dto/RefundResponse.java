package com.teamec2.paymentsystem.domain.refund.dto;

import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 환불 요청 응답 DTO입니다.
 *
 * @param refundId 환불 ID
 * @param refundAmount 환불 총 금액
 * @param pointRefundAmount 포인트 환불 금액
 * @param pgRefundAmount PG 환불 금액
 * @param reason 환불 사유
 * @param createdAt 환불 요청 생성일시
 * @param refundedAt 환불 완료일시
 * @param refundStatus 환불 상태
 * @param items 환불 상품 상세 목록
 */
public record RefundResponse(

        Long refundId,
        Long refundAmount,
        Long pointRefundAmount,
        Long pgRefundAmount,
        String reason,
        LocalDateTime createdAt,
        LocalDateTime refundedAt,
        RefundStatus refundStatus,
        List<RefundItemResponse> items

) {
    public static RefundResponse from(Refund refund, List<RefundItem> refundItems) {
        return new RefundResponse(
                refund.getId(),
                refund.getRefundAmount(),
                refund.getPointRefundAmount(),
                refund.getPgRefundAmount(),
                refund.getReason(),
                refund.getCreatedAt(),
                refund.getRefundedAt(),
                refund.getStatus(),
                refundItems.stream()
                        .map(RefundItemResponse::from)
                        .toList()
        );
    }
}
