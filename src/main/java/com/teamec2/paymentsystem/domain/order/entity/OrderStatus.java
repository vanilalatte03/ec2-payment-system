package com.teamec2.paymentsystem.domain.order.entity;

public enum OrderStatus {
    PAYMENT_PENDING,
    COMPLETED,
    PARTIAL_CANCELED,
    CANCELED;

    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            case PAYMENT_PENDING -> target == COMPLETED || target == PARTIAL_CANCELED || target == CANCELED;
            case COMPLETED -> target == CANCELED;
            case PARTIAL_CANCELED -> target == COMPLETED || target == CANCELED;
            case CANCELED -> false;
        };
    }
}
