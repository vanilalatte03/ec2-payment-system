package com.teamec2.paymentsystem.domain.order.entity;

import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.global.entity.BaseEntity;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "source_cart_item_id", nullable = false)
    private Long sourceCartItemId;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(nullable = false, columnDefinition = "int UNSIGNED")
    private int price;

    @Column(nullable = false, columnDefinition = "int UNSIGNED")
    private int quantity;

    // 환불 완료된 수량
    @Column(name = "refunded_quantity", nullable = false, columnDefinition = "int UNSIGNED DEFAULT 0")
    private int refundedQuantity = 0;

    // 환불 요청되어 처리 중인 수량
    @Column(name = "refund_reserved_quantity", nullable = false, columnDefinition = "int UNSIGNED DEFAULT 0")
    private int refundReservedQuantity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderItemStatus status = OrderItemStatus.ORDERED;

    public OrderItem(Order order, Product product, Long sourceCartItemId, int quantity) {
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (sourceCartItemId == null || sourceCartItemId <= 0) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }

        this.order = order;
        this.product = product;
        this.sourceCartItemId = sourceCartItemId;
        this.productName = product.getName();
        this.price = product.getPrice();
        this.quantity = quantity;
    }

    public long getSubtotal() {
        return (long) price * quantity;
    }

    /**
     * 아직 새 환불 요청에 사용할 수 있는 수량을 반환합니다.
     * 주문 수량에서 이미 환불 완료된 수량과 현재 환불 처리 중으로 예약된 수량을 모두 제외합니다.
     */
    public int getRemainingRefundableQuantity() {
        return quantity - refundedQuantity - refundReservedQuantity;
    }

    public boolean isCanceled() {
        return this.status == OrderItemStatus.CANCELED;
    }

    public void cancel() {
        cancel(this.product);
    }

    public void cancel(Product lockedProduct) {
        if (this.status == OrderItemStatus.CANCELED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (lockedProduct == null || !lockedProduct.getId().equals(getProductId())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        lockedProduct.restoreStock(this.quantity);
        this.status = OrderItemStatus.CANCELED;
    }

    /**
     * 환불 요청 생성 시점에 환불 수량을 예약합니다.
     * 아직 PG 환불이 완료된 것은 아니지만, 같은 주문 상품에 대해 중복 환불 요청이 들어와 환불 가능 수량을 초과하지 않도록 먼저 수량을 잡아둡니다.
     */
    public void reserveRefundQuantity(int refundQuantity) {
        validateRefundQuantity(refundQuantity);

        if (refundQuantity > getRemainingRefundableQuantity()) {
            throw new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        this.refundReservedQuantity += refundQuantity;
    }

    /**
     * 환불이 최종 완료되었을 때 호출합니다.
     * 예약해둔 환불 수량을 실제 환불 완료 수량으로 이동시키고,상품 재고를 복구합니다.
     */
    public void refund(int refundQuantity) {
        validateRefundQuantity(refundQuantity);

        if (refundQuantity > this.refundReservedQuantity) {
            throw new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        this.refundReservedQuantity -= refundQuantity;
        this.refundedQuantity += refundQuantity;
        this.product.restoreStock(refundQuantity);
    }

    /**
     * 환불이 최종 실패했을 때 예약된 환불 수량을 해제합니다.
     * PG 환불 실패, 재시도 초과, 환불 불가 확정 등으로 환불 처리를 더 이상 진행하지 않을 때 호출합니다.
     */
    public void releaseRefundQuantity(int refundQuantity) {
        validateRefundQuantity(refundQuantity);

        if (refundQuantity > this.refundReservedQuantity) {
            throw new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        this.refundReservedQuantity -= refundQuantity;
    }

    public Long getProductId() {
        return this.product.getId();
    }

    private static void validateRefundQuantity(int refundQuantity) {
        if (refundQuantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_QUANTITY);
        }
    }
}
