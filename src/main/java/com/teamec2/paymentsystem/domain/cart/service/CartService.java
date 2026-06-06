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

    /**
     * 결제 완료된 주문의 원본 장바구니 상품 ID만 삭제한다.
     *
     * <p>결제 확정 흐름에서 주문 상품에 저장된 {@code sourceCartItemId}를 기준으로 호출된다.
     * 클라이언트 요청에서 직접 CartItem 엔티티를 받는 경로와 달리 이미 생성된 주문의 원본 ID만
     * 사용하므로, 존재하지 않거나 이미 삭제된 항목은 삭제 대상에서 자연스럽게 제외한다.
     *
     * <p>{@code cartItemIds}가 {@code null}이거나 유효한 ID가 없고, 사용자 장바구니가 없는 경우에는
     * 예외 대신 삭제 수 {@code 0}을 반환해 결제 완료 처리가 불필요하게 실패하지 않도록 한다.
     *
     * @param userId 장바구니 소유 사용자 ID
     * @param cartItemIds 주문 상품에 기록된 원본 장바구니 상품 ID 목록
     * @return 삭제된 장바구니 상품 수
     */
    @Transactional
    public ClearCartResponse clearPurchasedItemIds(Long userId, List<Long> cartItemIds) {
        List<Long> distinctCartItemIds = cartItemIds == null
                ? List.of()
                : cartItemIds.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        if (distinctCartItemIds.isEmpty()) {
            return new ClearCartResponse(0);
        }

        return cartRepository.findByUserId(userId)
                .map(cart -> new ClearCartResponse(
                        cartItemRepository.deleteAllByCartIdAndIdIn(
                                cart.getId(),
                                distinctCartItemIds
                        )
                ))
                .orElseGet(() -> new ClearCartResponse(0));
    }
}
