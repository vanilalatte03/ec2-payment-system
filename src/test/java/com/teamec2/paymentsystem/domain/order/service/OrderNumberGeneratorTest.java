package com.teamec2.paymentsystem.domain.order.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNumberGeneratorTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Test
    void 주문번호는_날짜와_하이픈없는_전체UUID로_생성된다() {
        // given
        OrderNumberGenerator orderNumberGenerator = new OrderNumberGenerator();
        String today = LocalDate.now().format(DATE_FORMATTER);

        // when
        String orderNumber = orderNumberGenerator.generate();

        // then
        assertThat(orderNumber).matches("ORD-" + today + "-[0-9A-F]{32}");
    }

    @Test
    void 주문번호를_여러번_생성하면_서로_다른값이_나온다() {
        // given
        OrderNumberGenerator orderNumberGenerator = new OrderNumberGenerator();

        // when
        String firstOrderNumber = orderNumberGenerator.generate();
        String secondOrderNumber = orderNumberGenerator.generate();

        // then
        assertThat(firstOrderNumber).isNotEqualTo(secondOrderNumber);
    }
}
