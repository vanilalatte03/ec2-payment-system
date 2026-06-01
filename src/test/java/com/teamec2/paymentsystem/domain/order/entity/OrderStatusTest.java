package com.teamec2.paymentsystem.domain.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void 결제완료상태는_전액환불맥락에서_취소상태로_전이할수있다() {
        // when
        boolean canTransit = OrderStatus.COMPLETED.canTransitTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransit).isTrue();
    }
}
