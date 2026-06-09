package com.teamec2.paymentsystem.domain.order.facade;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCartFacadeTest {

    @Mock
    CartRepository cartRepository;

    @Mock
    CartItemRepository cartItemRepository;

    OrderCartFacade orderCartFacade;

    @BeforeEach
    void setUp() {
        orderCartFacade = new OrderCartFacade(cartRepository, cartItemRepository);
    }

    @Test
    void 장바구니조회_cartItemIds가없으면_전체상품을조회한다() {
        // given
        Cart cart = 장바구니(10L);
        CartItem cartItem = mock(CartItem.class);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllWithProductByCartId(10L)).thenReturn(List.of(cartItem));

        // when
        List<CartItem> cartItems = orderCartFacade.getCartItems(1L, null);

        // then
        assertThat(cartItems).containsExactly(cartItem);
        verify(cartItemRepository).findAllWithProductByCartId(10L);
        verify(cartItemRepository, never()).findAllWithProductByCartIdAndIdIn(10L, List.of());
    }

    @Test
    void 장바구니락조회_cartItemIds가없으면_상품패치없는전체조회메서드를사용한다() {
        // given
        Cart cart = 장바구니(10L);
        CartItem cartItem = mock(CartItem.class);

        when(cartRepository.findByUserIdWithOptimisticLock(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCartId(10L)).thenReturn(List.of(cartItem));

        // when
        List<CartItem> cartItems = orderCartFacade.getCartItemsWithLock(1L, List.of());

        // then
        assertThat(cartItems).containsExactly(cartItem);
        verify(cartItemRepository).findAllByCartId(10L);
        verify(cartItemRepository, never()).findAllByCartIdAndIdIn(10L, List.of());
    }

    @Test
    void 장바구니조회_선택ID가중복되면_중복제거후조회한다() {
        // given
        Cart cart = 장바구니(10L);
        CartItem firstItem = mock(CartItem.class);
        CartItem secondItem = mock(CartItem.class);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllWithProductByCartIdAndIdIn(10L, List.of(2L, 1L)))
                .thenReturn(List.of(firstItem, secondItem));

        // when
        List<CartItem> cartItems = orderCartFacade.getCartItems(1L, List.of(2L, 1L, 2L));

        // then
        assertThat(cartItems).containsExactly(firstItem, secondItem);
        verify(cartItemRepository).findAllWithProductByCartIdAndIdIn(10L, List.of(2L, 1L));
    }

    @Test
    void 장바구니조회_선택상품일부가없으면_CART_ITEM_NOT_FOUND가발생한다() {
        // given
        Cart cart = 장바구니(10L);
        CartItem cartItem = mock(CartItem.class);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllWithProductByCartIdAndIdIn(10L, List.of(1L, 2L)))
                .thenReturn(List.of(cartItem));

        // when
        // then
        assertThatThrownBy(() -> orderCartFacade.getCartItems(1L, List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    void 장바구니조회_장바구니가없거나조회결과가비어있으면_CART_EMPTY가발생한다() {
        // given
        Cart cart = 장바구니(10L);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.findByUserId(2L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllWithProductByCartId(10L)).thenReturn(List.of());

        // when
        // then
        assertThatThrownBy(() -> orderCartFacade.getCartItems(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_EMPTY);
        assertThatThrownBy(() -> orderCartFacade.getCartItems(2L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CART_EMPTY);
    }

    private Cart 장바구니(Long cartId) {
        User user = User.create("user-" + cartId + "@example.com", "Password123!", "홍길동", "010-1234-5678");
        Cart cart = new Cart(user);
        ReflectionTestUtils.setField(cart, "id", cartId);
        return cart;
    }
}
