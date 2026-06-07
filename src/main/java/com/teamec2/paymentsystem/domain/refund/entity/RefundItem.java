package com.teamec2.paymentsystem.domain.refund.entity;

import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 환불 요청 1건의 상품별 상세 기록
 * 환불 생성 시점의 주문 상품 단가, 환불 수량, 상품별 환불 금액을 스냅샷으로 저장합니다.
 * 이후 OrderItem 또는 상품 가격 정책이 변경되더라도 이미 생성된 환불 내역의 금액은 변경되지 않습니다.
 */
@Getter
@Entity
@Table(name = "refund_items",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refund_items_refund_order_item",
                        columnNames = {"refund_id", "order_item_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RefundItem {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @Column(name = "refund_quantity", nullable = false)
    private int refundQuantity;

    // 상품 단가
    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    // 상품별 총 환불 금액 = unitPrice * refundQuantity
    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "point_refund_amount", nullable = false)
    private Long pointRefundAmount;

    @Column(name = "pg_refund_amount", nullable = false)
    private Long pgRefundAmount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private RefundItem(
            Refund refund,
            OrderItem orderItem,
            int refundQuantity,
            Long pointRefundAmount,
            Long pgRefundAmount
    ) {
        validateRequiredValues(refund, orderItem);
        validateOrderItemBelongsToRefundOrder(refund, orderItem);
        validateQuantity(refundQuantity);
        validateRefundSourceAmounts(pointRefundAmount, pgRefundAmount);

        Long unitPrice = Long.valueOf(orderItem.getPrice());
        Long refundAmount = unitPrice * refundQuantity;

        validateUnitPrice(unitPrice);
        validateRefundAmount(refundAmount, pointRefundAmount, pgRefundAmount);

        this.refund = refund;
        this.orderItem = orderItem;
        this.refundQuantity = refundQuantity;
        this.unitPrice = unitPrice;
        this.refundAmount = refundAmount;
        this.pointRefundAmount = pointRefundAmount;
        this.pgRefundAmount = pgRefundAmount;
    }

    public static RefundItem createRefundItem(
            Refund refund,
            OrderItem orderItem,
            int refundQuantity,
            Long pointRefundAmount,
            Long pgRefundAmount
    ) {

        return new RefundItem(refund, orderItem, refundQuantity, pointRefundAmount, pgRefundAmount);
    }

    private static void validateRequiredValues(Refund refund, OrderItem orderItem) {

        if (refund == null || orderItem == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    private static void validateQuantity(int refundQuantity) {

        if (refundQuantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_QUANTITY);
        }
    }

    private static void validateUnitPrice(Long unitPrice) {
        if (unitPrice == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (unitPrice < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static void validateRefundSourceAmounts(
            Long pointRefundAmount,
            Long pgRefundAmount
    ) {
        if (pointRefundAmount == null || pgRefundAmount == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (pointRefundAmount < 0 || pgRefundAmount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * 상품 기준 환불 대상 금액보다 실제 반환 금액이 클 수는 없습니다.
     * refundAmount:
     * - 상품 가격 * 환불 수량
     * - 적립 포인트 회수 전 기준 금액
     * pointRefundAmount + pgRefundAmount:
     * - 적립 포인트 회수 정책을 반영한 뒤 고객에게 실제 반환되는 금액
     */
    private static void validateRefundAmount(
            Long refundAmount,
            Long pointRefundAmount,
            Long pgRefundAmount
    ) {
        long actualRefundAmount = pointRefundAmount + pgRefundAmount;

        if (actualRefundAmount > refundAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static void validateOrderItemBelongsToRefundOrder(Refund refund, OrderItem orderItem) {

        if (refund.getOrder() == null
                || refund.getOrder().getId() == null
                || orderItem.getOrder() == null
                || orderItem.getOrder().getId() == null
        ) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (!refund.getOrder().getId().equals(orderItem.getOrder().getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
