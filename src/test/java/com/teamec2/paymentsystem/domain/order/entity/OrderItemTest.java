package com.teamec2.paymentsystem.domain.order.entity;

import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @Test
    void 주문상품생성_가격이음수면_INVALID_ORDER_PRICE가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(order, product, -1, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_PRICE);
    }

    @Test
    void 주문상품생성_주문이없으면_ORDER_NOT_FOUND가발생한다() {
        // given
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(null, product, 1000, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    void 주문상품생성_상품이없으면_PRODUCT_NOT_FOUND가발생한다() {
        // given
        Order order = 주문_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(order, null, 1000, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 주문상품생성_수량이0이면_INVALID_ORDER_QUANTITY가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(order, product, 1000, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);
    }

    @Test
    void 주문상품생성_수량이음수면_INVALID_ORDER_QUANTITY가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(order, product, 1000, -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);
    }

    @Test
    void 주문상품금액조회_가격과수량이있으면_곱한금액을반환한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();
        OrderItem orderItem = new OrderItem(order, product, 1000, 3);

        // when
        long subtotal = orderItem.getSubtotal();

        // then
        assertThat(subtotal).isEqualTo(3000L);
    }

    @Test
    void 주문상품금액조회_int범위를넘어도_long타입금액을반환한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();
        OrderItem orderItem = new OrderItem(order, product, 1_500_000_000, 2);

        // when
        long subtotal = orderItem.getSubtotal();

        // then
        assertThat(subtotal).isEqualTo(3_000_000_000L);
    }

    private Order 주문_생성() {
        return Order.create(
                회원_생성(),
                "ORDER-001",
                1000,
                0
        );
    }

    private User 회원_생성() {
        return User.create(
                "test@example.com",
                "password",
                "테스트유저",
                "010-1234-5678"
        );
    }

    private Product 상품_생성() {
        return new Product(
                "테스트 상품",
                1000,
                10,
                "테스트 상품 설명",
                ProductStatus.ON_SALE,
                ProductCategory.ELECTRONIC
        );
    }
}
