package com.teamec2.paymentsystem.domain.order.entity;

public enum OrderStatus {
    PAYMENT_PENDING,
    COMPLETED,
    CANCELED;

    public boolean canCompletePayment() {
        return this == PAYMENT_PENDING;
    }

    public boolean canCancelPendingPayment() {
        return this == PAYMENT_PENDING;
    }

    public boolean canCancelCompletedByRefund() {
        return this == COMPLETED;
    }
}
