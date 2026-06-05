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

    @Column(name = "refunded_quantity", nullable = false, columnDefinition = "int UNSIGNED DEFAULT 0")
    private int refundedQuantity = 0;

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

    public int getRemainingRefundableQuantity() {
        return quantity - refundedQuantity;
    }

    public boolean isCanceled() {
        return this.status == OrderItemStatus.CANCELED;
    }

    public void cancel() {
        if (this.status == OrderItemStatus.CANCELED) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        this.product.restoreStock(this.quantity);
        this.status = OrderItemStatus.CANCELED;
    }

    public void refund(int refundQuantity) {
        if (refundQuantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_QUANTITY);
        }

        if (refundQuantity > getRemainingRefundableQuantity()) {
            throw new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED);
        }

        this.refundedQuantity += refundQuantity;
        this.product.restoreStock(refundQuantity);
    }

    public Long getProductId() {
        return this.product.getId();
    }
}
