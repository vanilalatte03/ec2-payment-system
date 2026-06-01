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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "order_number", unique = true, nullable = false, length = 100)
    private String orderNumber;

    @Column(name = "total_amount", nullable = false, columnDefinition = "int UNSIGNED")
    private int totalAmount;

    @Column(name = "used_point", nullable = false, columnDefinition = "int UNSIGNED")
    private int usedPoint = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    private Order(User user, String orderNumber, int totalAmount, int usedPoint) {
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (orderNumber == null || orderNumber.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (totalAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_PRICE);
        }

        if (usedPoint < 0) {
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

    public static Order create(User user, String orderNumber, int totalAmount, int usedPoint) {
        return new Order(user, orderNumber, totalAmount, usedPoint);
    }

    public void complete() {
        if (!status.canTransitTo(OrderStatus.COMPLETED)) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        this.status = OrderStatus.COMPLETED;
    }

    public void cancelPendingPayment() {
        changeStatus(OrderStatus.CANCELED);
    }

    public void cancelCompletedByRefund() {
        if (status != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        this.status = OrderStatus.CANCELED;
    }

    private void changeStatus(OrderStatus targetStatus) {
        if (!status.canTransitTo(targetStatus)) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        this.status = targetStatus;
    }
}
