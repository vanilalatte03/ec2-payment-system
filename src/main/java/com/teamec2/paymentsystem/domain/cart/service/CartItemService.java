package com.teamec2.paymentsystem.domain.cart.service;

import com.teamec2.paymentsystem.domain.cart.dto.CartItemCommandResponse;
import com.teamec2.paymentsystem.domain.cart.dto.DeleteCartItemResponse;
import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartItemService {

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final ProductRepository productRepository;

    @Transactional
    public CartItemCommandResponse addCartItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }

        Cart cart = cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateProductOnSale(product);

        CartItem cartItem = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), productId)
                .orElse(null);

        int finalQuantity;

        if (cartItem == null) {
            finalQuantity = quantity;
        } else {
            finalQuantity = cartItem.getQuantity() + quantity;
        }

        if (finalQuantity > product.getStock()) {
            throw new BusinessException(ErrorCode.CART_STOCK_EXCEEDED);
        }

        if (cartItem == null) {
            cartItem = new CartItem(cart, product, quantity);
        } else {
            cartItem.changeQuantity(finalQuantity);
        }

        CartItem savedCartItem = cartItemRepository.save(cartItem);
        Long cartTotalAmount = calculateCartTotalAmount(cart.getId());

        return toCommandResponse(savedCartItem, cartTotalAmount);
    }

    @Transactional
    public CartItemCommandResponse updateCartItemQuantity(Long userId, Long cartItemId, int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }

        CartItem cartItem = cartItemRepository.findWithOwnerAndProductById(cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!cartItem.getCart().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_ACCESS_DENIED);
        }

        Cart cart = cartRepository.findByIdWithOptimisticLock(cartItem.getCart().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        cartItem = cartItemRepository.findWithProductByCartIdAndId(cart.getId(), cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        Product product = cartItem.getProduct();

        validateProductOnSale(product);

        if (quantity > product.getStock()) {
            throw new BusinessException(ErrorCode.CART_STOCK_EXCEEDED);
        }

        cartItem.changeQuantity(quantity);

        Long cartTotalAmount = calculateCartTotalAmount(cart.getId());
        return toCommandResponse(cartItem, cartTotalAmount);
    }

    @Transactional
    public DeleteCartItemResponse deleteCartItem(Long userId, Long cartItemId) {
        CartItem cartItem = cartItemRepository.findWithOwnerAndProductById(cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!cartItem.getCart().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_ACCESS_DENIED);
        }

        Cart cart = cartRepository.findByIdWithOptimisticLock(cartItem.getCart().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        cartItem = cartItemRepository.findWithProductByCartIdAndId(cart.getId(), cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        cartItemRepository.delete(cartItem);
        cartItemRepository.flush();

        Long cartTotalAmount = calculateCartTotalAmount(cart.getId());
        return new DeleteCartItemResponse(true, cartItemId, cartTotalAmount);
    }

    private void validateProductOnSale(Product product) {
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
    }

    private CartItemCommandResponse toCommandResponse(CartItem cartItem, Long cartTotalAmount) {
        Product product = cartItem.getProduct();
        long lineAmount = (long) product.getPrice() * cartItem.getQuantity();

        return new CartItemCommandResponse(
                cartItem.getId(),
                product.getId(),
                product.getName(),
                cartItem.getQuantity(),
                product.getPrice(),
                lineAmount,
                cartTotalAmount
        );
    }

    private Long calculateCartTotalAmount(Long cartId) {
        return cartItemRepository.sumLineAmountByCartId(cartId);
    }
}
