package com.teamec2.paymentsystem.domain.refund.entity;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
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

class RefundItemTest {

    @Test
    void 환불상품생성_정상요청이면_상품별금액스냅샷을저장한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        OrderItem orderItem = 주문상품(payment.getOrder(), 100L, 3_000, 2);

        // when
        RefundItem refundItem = RefundItem.createRefundItem(refund, orderItem, 1, 200L, 2_800L);

        // then
        assertThat(refundItem.getRefund()).isSameAs(refund);
        assertThat(refundItem.getOrderItem()).isSameAs(orderItem);
        assertThat(refundItem.getRefundQuantity()).isEqualTo(1);
        assertThat(refundItem.getUnitPrice()).isEqualTo(3_000L);
        assertThat(refundItem.getRefundAmount()).isEqualTo(3_000L);
        assertThat(refundItem.getPointRefundAmount()).isEqualTo(200L);
        assertThat(refundItem.getPgRefundAmount()).isEqualTo(2_800L);
    }

    @Test
    void 환불상품생성_필수값이없으면_MISSING_REQUIRED_FIELD가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        OrderItem orderItem = 주문상품(payment.getOrder(), 100L, 3_000, 2);

        // when
        // then
        assertThatThrownBy(() -> RefundItem.createRefundItem(null, orderItem, 1, 0L, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> RefundItem.createRefundItem(refund, null, 1, 0L, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
        assertThatThrownBy(() -> RefundItem.createRefundItem(refund, orderItem, 1, null, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MISSING_REQUIRED_FIELD);
    }

    @Test
    void 환불상품생성_수량이0이면_INVALID_REFUND_QUANTITY가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        OrderItem orderItem = 주문상품(payment.getOrder(), 100L, 3_000, 2);

        // when
        // then
        assertThatThrownBy(() -> RefundItem.createRefundItem(refund, orderItem, 0, 0L, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_QUANTITY);
    }

    @Test
    void 환불상품생성_금액이음수이면_VALIDATION_FAILED가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        OrderItem orderItem = 주문상품(payment.getOrder(), 100L, 3_000, 2);

        // when
        // then
        assertThatThrownBy(() -> RefundItem.createRefundItem(refund, orderItem, 1, -1L, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 환불상품생성_실제반환금액이상품기준금액보다크면_VALIDATION_FAILED가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        OrderItem orderItem = 주문상품(payment.getOrder(), 100L, 3_000, 1);

        // when
        // then
        assertThatThrownBy(() -> RefundItem.createRefundItem(refund, orderItem, 1, 1_000L, 2_001L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 환불상품생성_주문상품이환불주문에속하지않으면_VALIDATION_FAILED가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        Order otherOrder = 주문(2L);
        OrderItem otherOrderItem = 주문상품(otherOrder, 200L, 3_000, 1);

        // when
        // then
        assertThatThrownBy(() -> RefundItem.createRefundItem(refund, otherOrderItem, 1, 0L, 1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void 환불상품생성_상품단가가음수이면_VALIDATION_FAILED가발생한다() {
        // given
        Payment payment = 결제(1L, 10L);
        Refund refund = 환불(payment);
        OrderItem orderItem = 주문상품(payment.getOrder(), 100L, 3_000, 1);
        ReflectionTestUtils.setField(orderItem, "price", -1);

        // when
        // then
        assertThatThrownBy(() -> RefundItem.createRefundItem(refund, orderItem, 1, 0L, 0L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    private Refund 환불(Payment payment) {
        return Refund.createRefund(
                "refund-key-" + payment.getId(),
                "request-hash-" + payment.getId(),
                payment.getOrder(),
                payment,
                "환불 사유",
                1_000L,
                200L,
                800L,
                250L,
                850L,
                100L,
                50L,
                0L,
                50L
        );
    }

    private Payment 결제(Long orderId, Long paymentId) {
        Order order = 주문(orderId);
        Payment payment = Payment.createPending(order, 1_000L, 200L, 800L, 8L);
        ReflectionTestUtils.setField(payment, "id", paymentId);
        return payment;
    }

    private Order 주문(Long orderId) {
        User user = User.create("user-" + orderId + "@example.com", "Password123!", "홍길동", "010-1234-5678");
        ReflectionTestUtils.setField(user, "id", orderId);
        Order order = Order.create(user, "ORDER-" + orderId, 1_000L, 200L);
        ReflectionTestUtils.setField(order, "id", orderId);
        return order;
    }

    private OrderItem 주문상품(Order order, Long orderItemId, int price, int quantity) {
        Product product = new Product(
                "테스트 상품",
                price,
                10,
                "테스트 상품 설명",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        );
        ReflectionTestUtils.setField(product, "id", orderItemId);
        OrderItem orderItem = new OrderItem(order, product, orderItemId, quantity);
        ReflectionTestUtils.setField(orderItem, "id", orderItemId);
        return orderItem;
    }
}
