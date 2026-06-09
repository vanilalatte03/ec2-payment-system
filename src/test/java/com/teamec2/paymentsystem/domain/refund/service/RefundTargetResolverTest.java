package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.refund.dto.RefundItemRequest;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundTargetResolverTest {

    RefundTargetResolver refundTargetResolver;

    @BeforeEach
    void setUp() {
        refundTargetResolver = new RefundTargetResolver();
    }

    @Test
    void 부분환불대상선정_같은주문상품ID가중복되면_DUPLICATE_REQUEST가발생한다() {
        // given
        OrderItem orderItem = 주문상품(1L, "후드 집업", 3_000, 2);

        // when
        // then
        assertThatThrownBy(() -> refundTargetResolver.resolvePartial(
                List.of(orderItem),
                List.of(
                        new RefundItemRequest(1L, 1),
                        new RefundItemRequest(1L, 1)
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_REQUEST);
    }

    @Test
    void 부분환불대상선정_예약수량을제외한잔여수량을초과하면_REFUND_QUANTITY_EXCEEDED가발생한다() {
        // given
        OrderItem orderItem = 주문상품(1L, "후드 집업", 3_000, 2);
        orderItem.reserveRefundQuantity(1);

        // when
        // then
        assertThatThrownBy(() -> refundTargetResolver.resolvePartial(
                List.of(orderItem),
                List.of(new RefundItemRequest(1L, 2))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_QUANTITY_EXCEEDED);
    }

    @Test
    void 부분환불대상선정_수량이0이면_INVALID_REFUND_QUANTITY가발생한다() {
        // given
        OrderItem orderItem = 주문상품(1L, "후드 집업", 3_000, 2);

        // when
        // then
        assertThatThrownBy(() -> refundTargetResolver.resolvePartial(
                List.of(orderItem),
                List.of(new RefundItemRequest(1L, 0))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFUND_QUANTITY);
    }

    @Test
    void 전체환불대상선정_예약으로환불가능수량이없는상품은제외하고_남은상품만선정한다() {
        // given
        OrderItem fullyReservedItem = 주문상품(1L, "후드 집업", 3_000, 1);
        fullyReservedItem.reserveRefundQuantity(1);
        OrderItem refundableItem = 주문상품(2L, "셔츠", 4_000, 2);

        // when
        RefundTarget target = refundTargetResolver.resolveFull(List.of(fullyReservedItem, refundableItem));

        // then
        assertThat(target.refundTargetItems()).containsExactly(refundableItem);
        assertThat(target.quantityMap()).containsEntry(2L, 2);
        assertThat(target.requestedRefundAmount()).isEqualTo(8_000L);
        assertThat(target.totalRemainingRefundableAmount()).isEqualTo(8_000L);
    }

    @Test
    void 전체환불대상선정_남은환불가능수량이없으면_REFUND_NOT_ALLOWED가발생한다() {
        // given
        OrderItem fullyReservedItem = 주문상품(1L, "후드 집업", 3_000, 1);
        fullyReservedItem.reserveRefundQuantity(1);

        // when
        // then
        assertThatThrownBy(() -> refundTargetResolver.resolveFull(List.of(fullyReservedItem)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REFUND_NOT_ALLOWED);
    }

    private OrderItem 주문상품(Long orderItemId, String productName, int price, int quantity) {
        User user = User.create(orderItemId + "@example.com", "Password123!", "홍길동", "010-1234-5678");
        Order order = Order.create(user, "ORDER-" + orderItemId, (long) price * quantity, 0L);
        Product product = new Product(
                productName,
                price,
                10,
                "테스트 상품",
                ProductStatus.ON_SALE,
                ProductCategory.TOP
        );
        OrderItem orderItem = new OrderItem(order, product, orderItemId, quantity);
        ReflectionTestUtils.setField(orderItem, "id", orderItemId);
        return orderItem;
    }
}
