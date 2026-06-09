package com.teamec2.paymentsystem.domain.order.facade;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderCartFacade {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public List<CartItem> getCartItems(Long userId, List<Long> cartItemIds) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));

        return getCartItems(cart, cartItemIds, true);
    }

    public List<CartItem> getCartItemsWithLock(Long userId, List<Long> cartItemIds) {
        Cart cart = cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));

        return getCartItems(cart, cartItemIds, false);
    }

    private List<CartItem> getCartItems(Cart cart, List<Long> cartItemIds, boolean withProduct) {
        List<Long> distinctCartItemIds = distinctIds(cartItemIds);
        List<CartItem> cartItems = findCartItems(cart.getId(), distinctCartItemIds, withProduct);

        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        if (hasMissingCartItem(distinctCartItemIds, cartItems)) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        return cartItems;
    }

    private List<CartItem> findCartItems(Long cartId, List<Long> cartItemIds, boolean withProduct) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return withProduct
                    ? cartItemRepository.findAllWithProductByCartId(cartId)
                    : cartItemRepository.findAllByCartId(cartId);
        }

        return withProduct
                ? cartItemRepository.findAllWithProductByCartIdAndIdIn(cartId, cartItemIds)
                : cartItemRepository.findAllByCartIdAndIdIn(cartId, cartItemIds);
    }

    private boolean hasMissingCartItem(List<Long> requestedIds, List<CartItem> cartItems) {
        return requestedIds != null
                && !requestedIds.isEmpty()
                && cartItems.size() != requestedIds.size();
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }

        return new LinkedHashSet<>(ids).stream()
                .toList();
    }
}
