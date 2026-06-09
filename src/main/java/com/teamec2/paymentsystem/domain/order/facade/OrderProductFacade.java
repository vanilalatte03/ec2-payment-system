package com.teamec2.paymentsystem.domain.order.facade;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OrderProductFacade {

    private final ProductRepository productRepository;

    public List<OrderProductTarget> lockOrderProducts(List<CartItem> cartItems) {
        List<Long> productIds = cartItems.stream()
                .map(cartItem -> cartItem.getProduct().getId())
                .toList();

        Map<Long, Product> lockedProducts = lockProducts(productIds);

        List<OrderProductTarget> orderTargets = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            Product product = lockedProducts.get(cartItem.getProduct().getId());
            if (product == null) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
            }

            orderTargets.add(new OrderProductTarget(cartItem, product));
        }

        return orderTargets;
    }

    public Map<Long, Product> lockProducts(List<Long> productIds) {
        List<Long> distinctProductIds = productIds.stream()
                .distinct()
                .sorted()
                .toList();

        if (distinctProductIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Product> lockedProducts = productRepository.findAllByIdsWithLock(distinctProductIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (lockedProducts.size() != distinctProductIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return lockedProducts;
    }

    public void validateCartProducts(List<CartItem> cartItems) {
        for (CartItem cartItem : cartItems) {
            validateProduct(cartItem.getProduct(), cartItem.getQuantity());
        }
    }

    public void validateOrderProducts(List<OrderProductTarget> orderTargets) {
        for (OrderProductTarget orderTarget : orderTargets) {
            validateProduct(orderTarget.product(), orderTarget.cartItem().getQuantity());
        }
    }

    public void decreaseStocks(List<OrderProductTarget> orderTargets) {
        for (OrderProductTarget orderTarget : orderTargets) {
            try {
                orderTarget.product().decreaseStock(orderTarget.cartItem().getQuantity());
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == ErrorCode.PRODUCT_OUT_OF_STOCK) {
                    throw new BusinessException(ErrorCode.ORDER_STOCK_SHORTAGE);
                }

                throw exception;
            }
        }
    }

    private void validateProduct(Product product, int quantity) {
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }

        if (product.getStock() < quantity) {
            throw new BusinessException(ErrorCode.ORDER_STOCK_SHORTAGE);
        }
    }
}
