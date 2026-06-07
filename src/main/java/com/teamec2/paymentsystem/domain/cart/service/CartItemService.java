package com.teamec2.paymentsystem.domain.cart.service;

import com.teamec2.paymentsystem.domain.cart.dto.AddItemResponse;
import com.teamec2.paymentsystem.domain.cart.dto.DeleteItemResponse;
import com.teamec2.paymentsystem.domain.cart.dto.UpdateQuantityResponse;
import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.facade.CartProductFacade;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.product.entity.Product;
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
    private final CartProductFacade cartProductFacade;

    @Transactional
    public AddItemResponse addItem(Long userId, Long productId, int quantity) {
        checkQuantity(quantity);

        Cart cart = getCartForEdit(userId);
        Product product = cartProductFacade.getProduct(productId);
        cartProductFacade.checkSale(product);

        CartItem cartItem = findItem(cart.getId(), productId);
        int finalQuantity = getFinalQuantity(cartItem, quantity);

        cartProductFacade.checkStock(product, finalQuantity);

        CartItem savedCartItem = saveItem(cartItem, cart, product, finalQuantity);

        return AddItemResponse.from(savedCartItem, totalAmount(cart.getId()));
    }

    @Transactional
    public UpdateQuantityResponse updateQuantity(Long userId, Long cartItemId, int quantity) {
        checkQuantity(quantity);

        CartItemTarget target = getOwnedItem(userId, cartItemId);
        Cart cart = target.cart();
        CartItem cartItem = target.cartItem();
        Product product = cartItem.getProduct();

        cartProductFacade.checkSale(product);
        cartProductFacade.checkStock(product, quantity);

        cartItem.changeQuantity(quantity);

        return UpdateQuantityResponse.from(cartItem, totalAmount(cart.getId()));
    }

    @Transactional
    public DeleteItemResponse deleteItem(Long userId, Long cartItemId) {
        CartItemTarget target = getOwnedItem(userId, cartItemId);
        Cart cart = target.cart();
        CartItem cartItem = target.cartItem();

        cartItemRepository.delete(cartItem);
        cartItemRepository.flush();

        return new DeleteItemResponse(true, cartItemId, totalAmount(cart.getId()));
    }

    private CartItemTarget getOwnedItem(Long userId, Long cartItemId) {
        CartItem cartItem = cartItemRepository.findWithOwnerAndProductById(cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        if (!cartItem.getCart().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CART_ITEM_ACCESS_DENIED);
        }

        Cart cart = cartRepository.findByIdWithOptimisticLock(cartItem.getCart().getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));

        CartItem target = cartItemRepository.findWithProductByCartIdAndId(cart.getId(), cartItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        return new CartItemTarget(cart, target);
    }

    private void checkQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }
    }

    private Cart getCartForEdit(Long userId) {
        return cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_NOT_FOUND));
    }

    private CartItem findItem(Long cartId, Long productId) {
        return cartItemRepository
                .findByCartIdAndProductId(cartId, productId)
                .orElse(null);
    }

    private int getFinalQuantity(CartItem cartItem, int quantity) {
        if (cartItem == null) {
            return quantity;
        }

        return cartItem.getQuantity() + quantity;
    }

    private CartItem saveItem(CartItem cartItem, Cart cart, Product product, int quantity) {
        if (cartItem == null) {
            return cartItemRepository.save(new CartItem(cart, product, quantity));
        }

        cartItem.changeQuantity(quantity);
        return cartItemRepository.save(cartItem);
    }

    private Long totalAmount(Long cartId) {
        return cartItemRepository.sumAmountByCartId(cartId);
    }

    private record CartItemTarget(
            Cart cart,
            CartItem cartItem
    ) {
    }
}
