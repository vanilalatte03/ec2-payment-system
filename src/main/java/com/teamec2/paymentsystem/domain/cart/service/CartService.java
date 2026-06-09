package com.teamec2.paymentsystem.domain.cart.service;

import com.teamec2.paymentsystem.domain.cart.dto.ClearCartResponse;
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

    /**
     * 회원의 장바구니와 장바구니 상품 목록을 조회합니다.
     *
     * 장바구니가 아직 없으면 예외를 던지지 않고 빈 장바구니 응답을 반환합니다.
     */
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> CartResponse.from(cart, findItems(cart)))
                .orElseGet(CartResponse::empty);
    }

    /**
     * 회원의 장바구니 상품을 전부 삭제합니다.
     *
     * 동시에 같은 장바구니를 수정하는 요청과 충돌하지 않도록 장바구니 버전을 증가시킵니다.
     */
    @Transactional
    public ClearCartResponse clearCart(Long userId) {
        Cart cart = getCartForEdit(userId);

        return deleteAllItems(cart);
    }

    /**
     * 전달받은 장바구니 상품 ID만 삭제합니다.
     *
     * 결제 완료 후 주문에 사용된 장바구니 상품만 정리할 때 사용합니다.
     * 이미 삭제된 상품 ID나 다른 회원의 상품 ID는 삭제 대상에서 자연스럽게 제외됩니다.
     * 동시에 같은 장바구니를 수정하는 요청과 충돌하지 않도록 장바구니 버전을 증가시킵니다.
     */
    @Transactional
    public ClearCartResponse clearItems(Long userId, List<Long> cartItemIds) {
        List<Long> targetIds = filterIds(cartItemIds);

        if (targetIds.isEmpty()) {
            return new ClearCartResponse(0);
        }

        return cartRepository.findByUserIdWithOptimisticLock(userId)
                .map(cart -> deleteItems(cart, targetIds))
                .orElseGet(() -> new ClearCartResponse(0));
    }

    @Transactional
    public ClearCartResponse clearPurchasedItemIds(Long userId, List<Long> cartItemIds) {
        return clearItems(userId, cartItemIds);
    }

    /**
     * 장바구니 화면에 상품 정보까지 보여줘야 하므로 Product를 함께 조회합니다.
     */
    private List<CartItem> findItems(Cart cart) {
        return cartItemRepository.findAllWithProductByCartId(cart.getId());
    }

    /**
     * 전체 삭제처럼 장바구니를 변경하는 작업에서 사용하는 낙관락 조회입니다.
     */
    private Cart getCartForEdit(Long userId) {
        return cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));
    }

    /**
     * 장바구니에 담긴 모든 상품을 삭제하고 삭제 개수를 응답으로 감쌉니다.
     */
    private ClearCartResponse deleteAllItems(Cart cart) {
        int deletedCount = (int) cartItemRepository.deleteByCartId(cart.getId());

        return new ClearCartResponse(deletedCount);
    }

    /**
     * 특정 장바구니에 속한 상품 중 전달받은 ID에 해당하는 상품만 삭제합니다.
     */
    private ClearCartResponse deleteItems(Cart cart, List<Long> cartItemIds) {
        int deletedCount = cartItemRepository.deleteByCartIdAndIdIn(cart.getId(), cartItemIds);

        return new ClearCartResponse(deletedCount);
    }

    /**
     * null, 0 이하, 중복 ID를 제거해 실제 삭제 조건에 사용할 ID 목록만 남깁니다.
     */
    private List<Long> filterIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }
}
