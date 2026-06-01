package com.teamec2.paymentsystem.domain.order.entity;

import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    @Test
    @DisplayName("주문 상품 가격은 0 이상이어야 한다")
    void createOrderItemWithNegativePriceThrowsException() {
        Order order = createOrder();
        Product product = createProduct();

        assertThatThrownBy(() -> new OrderItem(order, product, -1, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_PRICE);
    }

    @Test
    @DisplayName("주문 상품 생성 시 주문은 필수다")
    void createOrderItemWithoutOrderThrowsException() {
        Product product = createProduct();

        assertThatThrownBy(() -> new OrderItem(null, product, 1000, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 상품 생성 시 상품은 필수다")
    void createOrderItemWithoutProductThrowsException() {
        Order order = createOrder();

        assertThatThrownBy(() -> new OrderItem(order, null, 1000, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 상품 수량은 1 이상이어야 한다")
    void createOrderItemWithZeroOrNegativeQuantityThrowsException() {
        Order order = createOrder();
        Product product = createProduct();

        assertThatThrownBy(() -> new OrderItem(order, product, 1000, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);

        assertThatThrownBy(() -> new OrderItem(order, product, 1000, -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);
    }

    @Test
    @DisplayName("주문 상품 금액은 가격과 수량을 곱해 계산한다")
    void getSubtotal() {
        Order order = createOrder();
        Product product = createProduct();
        OrderItem orderItem = new OrderItem(order, product, 1000, 3);

        assertThat(orderItem.getSubtotal()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("주문 상품 금액이 int 범위를 넘어도 정상 계산한다")
    void getSubtotalOverIntegerMaxValue() {
        Order order = createOrder();
        Product product = createProduct();
        OrderItem orderItem = new OrderItem(order, product, 1_500_000_000, 2);

        assertThat(orderItem.getSubtotal()).isEqualTo(3_000_000_000L);
    }

    private Order createOrder() {
        return Order.create(
                createUser(),
                "ORDER-001",
                1000,
                0
        );
    }

    private User createUser() {
        return User.create(
                "test@example.com",
                "password",
                "테스트유저",
                "010-1234-5678"
        );
    }

    private Product createProduct() {
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
