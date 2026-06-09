package com.teamec2.paymentsystem.domain.refund.service;


import com.teamec2.paymentsystem.domain.order.entity.OrderItem;

import java.util.List;
import java.util.Map;

/**
 * RefundTargetResolver의 결과를 담는 결과지
 * 환불 대상 상품과 환불 수량 계산 결과입니다.
 *
 * @param refundTargetItems 이번 환불 대상 주문 상품 목록
 * @param quantityMap orderItemId -> 환불 수량
 * @param requestedRefundAmount 이번 요청에서 환불하려는 상품 기준 금액
 * @param totalRemainingRefundableAmount 현재 주문에서 아직 환불 가능한 전체 잔여 금액
 */
public record RefundTarget (
        List<OrderItem> refundTargetItems,
        Map<Long, Integer> quantityMap,
        long requestedRefundAmount,
        long totalRemainingRefundableAmount
) {
}
