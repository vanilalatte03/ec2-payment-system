package com.teamec2.paymentsystem.domain.refund.service;


import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.refund.dto.RefundItemRequest;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 환불 대상을 찾아주는 계산/판단 담당자
 * 환불 요청을 기준으로 실제 환불 대상 상품과 수량을 결정합니다.
 * 이 클래스는 다음 책임을 가집니다.
 * 1. 부분 환불 요청의 상품/수량 검증
 * 2. 전체 환불 대상 상품/수량 결정
 * 3. orderItemId -> 환불 수량 Map 생성
 * 4. 이번 요청 환불 금액 계산
 * 5. 주문 전체의 남은 환불 가능 금액 계산
 * 실제 Refund, RefundItem, RefundOutbox 저장은 담당하지 않습니다.
 */
@Component
public class RefundTargetResolver {
    /**
     * 부분 환불 요청을 기준으로 환불 대상 상품과 수량을 결정합니다.
     */
    public RefundTarget resolvePartial(
            List<OrderItem> orderItems,
            List<RefundItemRequest> itemRequests
    ) {
        List<OrderItem> refundTargetItems = findPartialRefundItems(orderItems, itemRequests);
        Map<Long, Integer> quantityMap = toQuantityMap(itemRequests);

        long requestedRefundAmount = calculateRequestedRefundAmount(refundTargetItems, quantityMap);
        long totalRemainingRefundableAmount = calculateTotalRemainingRefundableAmount(orderItems);

        return new RefundTarget(
                refundTargetItems,
                quantityMap,
                requestedRefundAmount,
                totalRemainingRefundableAmount
        );
    }

    /**
     * 전체 환불 요청을 기준으로 환불 대상 상품과 수량을 결정합니다.
     */
    public RefundTarget resolveFull(List<OrderItem> orderItems) {
        List<OrderItem> refundTargetItems = orderItems.stream()
                .filter(orderItem -> orderItem.getRemainingRefundableQuantity() > 0)
                .toList();

        if (refundTargetItems.isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        Map<Long, Integer> quantityMap = new HashMap<>();

        for (OrderItem orderItem : refundTargetItems) {
            quantityMap.put(orderItem.getId(), orderItem.getRemainingRefundableQuantity());
        }

        long totalRemainingRefundableAmount = calculateTotalRemainingRefundableAmount(orderItems);

        /*
         * 전체 환불에서는 이번 요청 환불 금액과
         * 전체 남은 환불 가능 금액이 같습니다.
         */
        return new RefundTarget(
                refundTargetItems,
                quantityMap,
                totalRemainingRefundableAmount,
                totalRemainingRefundableAmount
        );
    }


    /**
     * 부분 환불 요청으로 들어온 상품 목록이 실제 환불 가능한지 검증하고,
     * 환불 대상 OrderItem 목록을 반환합니다.
     */
    private List<OrderItem> findPartialRefundItems(
            List<OrderItem> orderItems,
            List<RefundItemRequest> itemRequests
    ) {
        Map<Long, OrderItem> orderItemMap = new HashMap<>();

        for (OrderItem orderItem : orderItems) {
            orderItemMap.put(orderItem.getId(), orderItem);
        }

        Set<Long> requestedItemIds = new HashSet<>();
        List<OrderItem> refundTargetItems = new ArrayList<>();

        for (RefundItemRequest itemRequest : itemRequests) {
            if (!requestedItemIds.add(itemRequest.orderItemId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
            }

            OrderItem orderItem = orderItemMap.get(itemRequest.orderItemId());

            if (orderItem == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }

            if (itemRequest.quantity() == null || itemRequest.quantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_REFUND_QUANTITY);
            }

            if (itemRequest.quantity() > orderItem.getRemainingRefundableQuantity()) {
                throw new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED);
            }

            refundTargetItems.add(orderItem);
        }

        return refundTargetItems;
    }

    /**
     * 부분 환불 요청의 item 목록을 orderItemId -> quantity 형태로 변환합니다.
     */
    private Map<Long, Integer> toQuantityMap(List<RefundItemRequest> itemRequests) {
        Map<Long, Integer> quantityMap = new HashMap<>();

        for (RefundItemRequest itemRequest : itemRequests) {
            quantityMap.put(itemRequest.orderItemId(), itemRequest.quantity());
        }

        return quantityMap;
    }

    /**
     * 이번 환불 요청의 상품 기준 총 환불 금액을 계산합니다.
     */
    private long calculateRequestedRefundAmount(
            List<OrderItem> refundTargetItems,
            Map<Long, Integer> quantityMap
    ) {
        long refundAmount = 0L;

        for (OrderItem orderItem : refundTargetItems) {
            refundAmount += (long) orderItem.getPrice() * quantityMap.get(orderItem.getId());
        }

        if (refundAmount <= 0) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        return refundAmount;
    }

    /**
     * 현재 주문에서 아직 환불 가능한 전체 잔여 금액을 계산합니다.
     */
    private long calculateTotalRemainingRefundableAmount(List<OrderItem> orderItems) {
        long totalRemainingAmount = 0L;

        for (OrderItem orderItem : orderItems) {
            totalRemainingAmount += (long) orderItem.getPrice() * orderItem.getRemainingRefundableQuantity();
        }

        if (totalRemainingAmount <= 0) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        return totalRemainingAmount;
    }
}
