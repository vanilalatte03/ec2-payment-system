package com.teamec2.paymentsystem.domain.cart.service;

import com.teamec2.paymentsystem.domain.cart.dto.ClearCartResponse;
import com.teamec2.paymentsystem.domain.cart.dto.CartItemResponse;
import com.teamec2.paymentsystem.domain.cart.dto.CartResponse;
import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> {
                    List<CartItem> items = cartItemRepository.findAllWithProduct(cart.getId());

                    List<CartItemResponse> itemResponses = items.stream()
                            .map(item -> new CartItemResponse(
                                    item.getId(),
                                    item.getProduct().getId(),
                                    item.getProduct().getName(),
                                    item.getQuantity(),
                                    item.getProduct().getPrice(),
                                    (long) item.getProduct().getPrice() * item.getQuantity(),
                                    item.getProduct().getStock(),
                                    item.getProduct().getStatus()
                            ))
                            .toList();

                    int totalQuantity = itemResponses.stream()
                            .mapToInt(CartItemResponse::quantity)
                            .sum();

                    Long totalAmount = itemResponses.stream()
                            .mapToLong(CartItemResponse::lineAmount)
                            .sum();

                    return new CartResponse(cart.getId(), itemResponses, totalQuantity, totalAmount);
                })
                .orElseGet(() -> new CartResponse(null, List.of(), 0, 0L));
    }

    @Transactional
    public ClearCartResponse clearCart(Long userId) {
        Cart cart = cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        int deletedCount = (int) cartItemRepository.deleteByCartId(cart.getId());

        return new ClearCartResponse(deletedCount);
    }

    @Transactional
    public ClearCartResponse clearPurchasedItems(Long userId, List<CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            return new ClearCartResponse(0);
        }

        Cart cart = cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        List<Long> cartItemIds = cartItems.stream()
                .filter(Objects::nonNull)
                .map(CartItem::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (cartItemIds.isEmpty()) {
            return new ClearCartResponse(0);
        }

        List<CartItem> deleteTargets = cartItemRepository.findAllById(cartItemIds);

        if (deleteTargets.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        boolean hasOtherCartItem = deleteTargets.stream()
                .anyMatch(cartItem -> !Objects.equals(cartItem.getCart().getId(), cart.getId()));

        if (hasOtherCartItem) {
            throw new BusinessException(ErrorCode.CART_ITEM_ACCESS_DENIED);
        }

        cartItemRepository.deleteAll(deleteTargets);

        return new ClearCartResponse(deleteTargets.size());
    }
}
