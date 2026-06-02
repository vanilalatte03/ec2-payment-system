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

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> {
                    List<CartItem> items = cartItemRepository.findAllByCartIdWithProduct(cart.getId());

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
        Cart cart = cartRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        int deletedCount = cartItemRepository.countByCartId(cart.getId());
        cartItemRepository.deleteAllByCartId(cart.getId());

        return new ClearCartResponse(deletedCount);
    }
}
