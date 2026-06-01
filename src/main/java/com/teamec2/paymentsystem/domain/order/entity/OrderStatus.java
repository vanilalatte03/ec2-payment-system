package com.teamec2.paymentsystem.domain.order.entity;

public enum OrderStatus {
    PAYMENT_PENDING {
        @Override
        public boolean canTransitTo(OrderStatus target) {
            return target == COMPLETED || target == CANCELED;
        }
    },
    COMPLETED {
        @Override
        public boolean canTransitTo(OrderStatus target) {
            return target == CANCELED;
        }
    },
    CANCELED {
        @Override
        public boolean canTransitTo(OrderStatus target) {
            return false;
        }
    };

    public abstract boolean canTransitTo(OrderStatus target);
}
