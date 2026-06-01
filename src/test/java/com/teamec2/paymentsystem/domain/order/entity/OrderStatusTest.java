package com.teamec2.paymentsystem.domain.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void 주문상태는_결제대기_결제완료_취소를_가진다() {
        // when
        OrderStatus[] statuses = OrderStatus.values();

        // then
        assertThat(statuses).containsExactly(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.COMPLETED,
                OrderStatus.CANCELED
        );
    }
}
