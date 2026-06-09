package com.teamec2.paymentsystem.domain.order.facade;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderProductFacadeTest {

    @Mock
    ProductRepository productRepository;

    OrderProductFacade orderProductFacade;

    @BeforeEach
    void setUp() {
        orderProductFacade = new OrderProductFacade(productRepository);
    }

    @Test
    void 상품락조회_빈목록이면_저장소를호출하지않고빈Map을반환한다() {
        // given

        // when
        Map<Long, Product> lockedProducts = orderProductFacade.lockProducts(List.of());

        // then
        assertThat(lockedProducts).isEmpty();
        verify(productRepository, never()).findAllByIdsWithLock(anyList());
    }

    @Test
    void 상품락조회_중복ID를정렬해조회하고_상품Map을반환한다() {
        // given
        Product firstProduct = 상품(1L, 1_000, 10, ProductStatus.ON_SALE);
        Product secondProduct = 상품(2L, 2_000, 10, ProductStatus.ON_SALE);

        when(productRepository.findAllByIdsWithLock(List.of(1L, 2L)))
                .thenReturn(List.of(firstProduct, secondProduct));

        // when
        Map<Long, Product> lockedProducts = orderProductFacade.lockProducts(List.of(2L, 1L, 2L));

        // then
        assertThat(lockedProducts).containsEntry(1L, firstProduct)
                .containsEntry(2L, secondProduct);
        verify(productRepository).findAllByIdsWithLock(List.of(1L, 2L));
    }

    @Test
    void 상품락조회_조회결과가부족하면_PRODUCT_NOT_FOUND가발생한다() {
        // given
        Product firstProduct = 상품(1L, 1_000, 10, ProductStatus.ON_SALE);

        when(productRepository.findAllByIdsWithLock(List.of(1L, 2L)))
                .thenReturn(List.of(firstProduct));

        // when
        // then
        assertThatThrownBy(() -> orderProductFacade.lockProducts(List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 주문대상락조회_장바구니상품의락상품을_주문대상으로반환한다() {
        // given
        Product product = 상품(1L, 1_000, 10, ProductStatus.ON_SALE);
        CartItem cartItem = 장바구니상품(product);

        when(productRepository.findAllByIdsWithLock(List.of(1L))).thenReturn(List.of(product));

        // when
        List<OrderProductTarget> orderTargets = orderProductFacade.lockOrderProducts(List.of(cartItem));

        // then
        assertThat(orderTargets).hasSize(1);
        assertThat(orderTargets.get(0).cartItem()).isSameAs(cartItem);
        assertThat(orderTargets.get(0).product()).isSameAs(product);
    }

    @Test
    void 장바구니상품검증_판매중이아니면_PRODUCT_NOT_ON_SALE가발생한다() {
        // given
        CartItem cartItem = 장바구니상품(상품(1L, 1_000, 10, ProductStatus.SOLD_OUT), 1);

        // when
        // then
        assertThatThrownBy(() -> orderProductFacade.validateCartProducts(List.of(cartItem)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    void 주문상품검증_재고가부족하면_ORDER_STOCK_SHORTAGE가발생한다() {
        // given
        Product product = 상품(1L, 1_000, 1, ProductStatus.ON_SALE);
        OrderProductTarget orderTarget = new OrderProductTarget(장바구니상품수량(2), product);

        // when
        // then
        assertThatThrownBy(() -> orderProductFacade.validateOrderProducts(List.of(orderTarget)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_STOCK_SHORTAGE);
    }

    @Test
    void 재고차감_상품재고부족예외를_ORDER_STOCK_SHORTAGE로변환한다() {
        // given
        Product product = 상품(1L, 1_000, 0, ProductStatus.ON_SALE);
        OrderProductTarget orderTarget = new OrderProductTarget(장바구니상품수량(1), product);

        // when
        // then
        assertThatThrownBy(() -> orderProductFacade.decreaseStocks(List.of(orderTarget)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_STOCK_SHORTAGE);
    }

    private Product 상품(Long productId, int price, int stock, ProductStatus status) {
        Product product = new Product(
                "테스트 상품",
                price,
                stock,
                "테스트 상품 설명",
                status,
                ProductCategory.TOP
        );
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }

    private CartItem 장바구니상품(Product product, int quantity) {
        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getProduct()).thenReturn(product);
        when(cartItem.getQuantity()).thenReturn(quantity);
        return cartItem;
    }

    private CartItem 장바구니상품(Product product) {
        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getProduct()).thenReturn(product);
        return cartItem;
    }

    private CartItem 장바구니상품수량(int quantity) {
        CartItem cartItem = mock(CartItem.class);
        when(cartItem.getQuantity()).thenReturn(quantity);
        return cartItem;
    }
}
