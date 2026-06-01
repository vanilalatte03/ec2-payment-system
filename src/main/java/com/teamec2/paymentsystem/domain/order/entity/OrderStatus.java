package com.teamec2.paymentsystem.domain.order.entity;

public enum OrderStatus {
    PAYMENT_PENDING,
    COMPLETED,
    CANCELED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PAYMENT_PENDING -> target == COMPLETED || target == CANCELED;
            case COMPLETED -> target == CANCELED;
            case CANCELED -> false;
        };
    }
}
