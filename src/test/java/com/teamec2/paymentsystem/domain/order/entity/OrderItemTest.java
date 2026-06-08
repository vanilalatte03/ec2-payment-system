package com.teamec2.paymentsystem.domain.order.entity;

import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderItemTest {

    private static final Long SOURCE_CART_ITEM_ID = 1L;

    @Test
    void 주문상품생성_상품가격을주문상품가격으로저장한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성(1500);

        // when
        OrderItem orderItem = new OrderItem(order, product, SOURCE_CART_ITEM_ID, 2);

        // then
        assertThat(orderItem.getPrice()).isEqualTo(1500);
    }

    @Test
    void 주문상품생성_원본장바구니상품ID를저장한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();

        // when
        OrderItem orderItem = new OrderItem(order, product, 123L, 2);

        // then
        assertThat(orderItem.getSourceCartItemId()).isEqualTo(123L);
    }

    @Test
    void 주문상품생성_주문이없으면_ORDER_NOT_FOUND가발생한다() {
        // given
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(null, product, SOURCE_CART_ITEM_ID, 1))
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
        assertThatThrownBy(() -> new OrderItem(order, null, SOURCE_CART_ITEM_ID, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 주문상품생성_원본장바구니상품ID가없으면_CART_ITEM_NOT_FOUND가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(order, product, null, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void 주문상품생성_원본장바구니상품ID가0이면_CART_ITEM_NOT_FOUND가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(order, product, 0L, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void 주문상품생성_수량이0이면_INVALID_ORDER_QUANTITY가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();

        // when
        // then
        assertThatThrownBy(() -> new OrderItem(order, product, SOURCE_CART_ITEM_ID, 0))
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
        assertThatThrownBy(() -> new OrderItem(order, product, SOURCE_CART_ITEM_ID, -1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_ORDER_QUANTITY);
    }

    @Test
    void 주문상품금액조회_가격과수량이있으면_곱한금액을반환한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();
        OrderItem orderItem = new OrderItem(order, product, SOURCE_CART_ITEM_ID, 3);

        // when
        long subtotal = orderItem.getSubtotal();

        // then
        assertThat(subtotal).isEqualTo(3000L);
    }

    @Test
    void 주문상품금액조회_int범위를넘어도_long타입금액을반환한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성(1_500_000_000);
        OrderItem orderItem = new OrderItem(order, product, SOURCE_CART_ITEM_ID, 2);

        // when
        long subtotal = orderItem.getSubtotal();

        // then
        assertThat(subtotal).isEqualTo(3_000_000_000L);
    }

    @Test
    void 주문상품환불_수량만큼_환불수량을누적하고_상품재고를복구한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();
        OrderItem orderItem = new OrderItem(order, product, SOURCE_CART_ITEM_ID, 3);

        // when
        orderItem.reserveRefundQuantity(2);
        orderItem.refund(2, product);

        // then
        assertThat(orderItem.getRefundedQuantity()).isEqualTo(2);
        assertThat(orderItem.getRefundReservedQuantity()).isZero();
        assertThat(orderItem.getRemainingRefundableQuantity()).isEqualTo(1);
        assertThat(product.getStock()).isEqualTo(12);
    }

    @Test
    void 주문상품환불_잔여환불가능수량을초과하면_REFUND_QUANTITY_EXCEEDED가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();
        OrderItem orderItem = new OrderItem(order, product, SOURCE_CART_ITEM_ID, 3);
        orderItem.reserveRefundQuantity(2);
        orderItem.refund(2, product);

        // when
        // then
        assertThatThrownBy(() -> orderItem.reserveRefundQuantity(2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_QUANTITY_EXCEEDED);
    }

    @Test
    void 주문상품환불_수량이0이면_INVALID_REFUND_QUANTITY가발생한다() {
        // given
        Order order = 주문_생성();
        Product product = 상품_생성();
        OrderItem orderItem = new OrderItem(order, product, SOURCE_CART_ITEM_ID, 3);

        // when
        // then
        assertThatThrownBy(() -> orderItem.refund(0, product))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_QUANTITY);
    }

    private Order 주문_생성() {
        return Order.create(
                회원_생성(),
                "ORDER-001",
                1000L,
                0L
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
        return 상품_생성(1000);
    }

    private Product 상품_생성(int price) {
        Product product = new Product(
                "테스트 상품",
                price,
                10,
                "테스트 상품 설명",
                ProductStatus.ON_SALE,
                ProductCategory.ELECTRONIC
        );

        ReflectionTestUtils.setField(product, "id", 1L);

        return product;
    }
}
