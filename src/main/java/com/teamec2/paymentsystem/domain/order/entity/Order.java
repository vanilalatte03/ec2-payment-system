package com.teamec2.paymentsystem.domain.order.entity;

import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.entity.BaseEntity;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_number", unique = true, nullable = false, length = 100)
    private String orderNumber;

    @Column(name = "total_amount", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long totalAmount;

    @Column(name = "used_point", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long usedPoint = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    private Order(User user, String orderNumber, Long totalAmount, Long usedPoint) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (orderNumber == null || orderNumber.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (totalAmount == null || totalAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_PRICE);
        }

        if (usedPoint == null || usedPoint < 0) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }

        if (usedPoint > totalAmount) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }

        this.user = user;
        this.orderNumber = orderNumber;
        this.totalAmount = totalAmount;
        this.usedPoint = usedPoint;
        this.status = OrderStatus.PAYMENT_PENDING;
    }

    public static Order create(User user, String orderNumber, Long totalAmount, Long usedPoint) {
        return new Order(user, orderNumber, totalAmount, usedPoint);
    }

    public boolean isPaymentPending() {
        return status == OrderStatus.PAYMENT_PENDING;
    }

    public void complete() {
        changeStatus(OrderStatus.COMPLETED, ErrorCode.INVALID_ORDER_STATUS);
    }

    public void cancelPendingPayment() {
        changeStatusFrom(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELED, ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
    }

    public void cancelCompletedByRefund() {
        changeStatusFrom(OrderStatus.COMPLETED, OrderStatus.CANCELED, ErrorCode.REFUND_NOT_ALLOWED);
    }

    private void changeStatus(OrderStatus targetStatus, ErrorCode errorCode) {
        if (!status.canTransitionTo(targetStatus)) {
            throw new BusinessException(errorCode);
        }

        this.status = targetStatus;
    }

    private void changeStatusFrom(OrderStatus sourceStatus, OrderStatus targetStatus, ErrorCode errorCode) {
        if (status != sourceStatus) {
            throw new BusinessException(errorCode);
        }

        changeStatus(targetStatus, errorCode);
    }
}
