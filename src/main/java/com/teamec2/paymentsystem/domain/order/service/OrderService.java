package com.teamec2.paymentsystem.domain.order.service;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.dto.CreateOrderResponse;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.service.PointPolicy;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final PointPolicy pointPolicy;
    private final PointService pointService;

    // 주문 생성, 재고 선차감, 결제 대기 생성은 하나의 작업처럼 성공하거나 실패해야 합니다.
    // 그래서 중간에 재고 부족 같은 예외가 발생하면 전체 DB 변경이 롤백되도록 @Transactional을 사용합니다.
    @Transactional
    public CreateOrderResponse createOrder(Long userId, List<Long> cartItemIds, Long usePointAmount) {
        validateUsedPoint(usePointAmount);
        List<Long> distinctCartItemIds = distinctCartItemIds(cartItemIds);

        // 인증된 회원이 실제로 존재하는지 확인합니다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 주문/결제 데이터를 만들기 전에 포인트 부족 여부를 빠르게 확인합니다.
        // 이 조회는 잠금 없는 1차 검증입니다.
        // 실제 예약 차감과 동시성 검증은 PointService.reserveUsedPoints()에서
        // user row에 비관락을 걸고 다시 수행합니다.
        if (user.getPointBalance() < usePointAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }

        // 주문은 장바구니에 담긴 상품을 기준으로 생성하므로 회원의 장바구니가 필요합니다.
        Cart cart = cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));

        // cartItemIds가 있으면 선택 상품만, 없으면 장바구니 전체 상품을 주문 대상으로 가져옵니다.
        List<CartItem> cartItems = findOrderTargetCartItems(cart.getId(), distinctCartItemIds);
        validateCartItems(cartItems, distinctCartItemIds);

        // 주문 총액은 주문 생성 순간의 상품 가격과 장바구니 수량으로 계산합니다.
        Long totalAmount = calculateTotalAmount(cartItems);

        if (usePointAmount > totalAmount) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }

        // 결제 완료 시점이 아니라 주문 생성 시점에 재고를 먼저 차감합니다.
        // 이 반복문 중 하나라도 실패하면 @Transactional 때문에 앞선 차감도 함께 롤백됩니다.
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            if (product.getStatus() != ProductStatus.ON_SALE) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
            }

            if (product.getStock() < cartItem.getQuantity()) {
                throw new BusinessException(ErrorCode.ORDER_STOCK_SHORTAGE);
            }

            product.decreaseStock(cartItem.getQuantity());
        }

        // 실제 포인트 적립이 아닌 적립 예정 포인트 계산입니다.
        Long pgAmount = totalAmount - usePointAmount;
        Long rewardPointAmount = pointPolicy.calculateRewardPoint(pgAmount);

        // 주문은 처음에는 결제대기 상태로 생성됩니다.
        Order order = Order.create(
                user,
                orderNumberGenerator.generate(),
                totalAmount,
                usePointAmount
        );

        Order savedOrder = orderRepository.save(order);

        // 주문 상품에는 현재 상품명과 가격이 스냅샷으로 저장됩니다.
        // 상품명이 나중에 바뀌어도 이 주문의 상품명은 주문 당시 값으로 남습니다.
        List<OrderItem> orderItems = cartItems.stream()
                .map(cartItem -> new OrderItem(savedOrder, cartItem.getProduct(), cartItem.getQuantity()))
                .toList();

        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        // PortOne에 전달할 portonePaymentId는 Payment.createPending 내부에서 미리 생성됩니다.
        // 아직 실제 결제가 끝난 것이 아니므로 결제 상태는 PENDING입니다.
        Payment payment = Payment.createPending(
                savedOrder,
                totalAmount,
                usePointAmount,
                pgAmount,
                rewardPointAmount
        );
        Payment savedPayment = paymentRepository.save(payment);

        // 결제 대기 상태에서 사용할 포인트를 예약 차감합니다.
        // 예약에 실패하면 @Transactional 때문에 주문/결제/재고 변경도 함께 롤백됩니다.
        pointService.reserveUsedPoints(savedPayment);

        // 장바구니는 여기서 비우지 않습니다.
        // 비우는 시점은 비즈니스 규칙대로 결제 완료 이후가 되어야 합니다.
        return toResponse(
                savedOrder,
                savedPayment,
                savedOrderItems
        );
    }

    // 사용 포인트는 null이 아니고 0 이상이어야 합니다.
    private void validateUsedPoint(Long usePointAmount) {
        if (usePointAmount == null || usePointAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }
    }

    // 요청에 같은 장바구니 상품 ID가 반복되면 같은 상품을 한 번만 주문 대상으로 처리합니다.
    private List<Long> distinctCartItemIds(List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return cartItemIds;
        }

        return new LinkedHashSet<>(cartItemIds).stream()
                .toList();
    }

    // 선택 주문이면 cartItemIds에 해당하는 상품만 조회하고, 전체 주문이면 장바구니 전체 상품을 조회합니다.
    private List<CartItem> findOrderTargetCartItems(Long cartId, List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return cartItemRepository.findOrderItemsByCartId(cartId);
        }

        return cartItemRepository.findOrderItemsByCartIdAndIdIn(cartId, cartItemIds);
    }

    // 주문 대상이 비어 있거나, 요청한 장바구니 상품 일부를 찾지 못한 경우 주문을 만들 수 없습니다.
    private void validateCartItems(List<CartItem> cartItems, List<Long> cartItemIds) {
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        if (cartItemIds != null && !cartItemIds.isEmpty() && cartItems.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }
    }

    // 각 줄의 금액은 현재 상품 가격 * 장바구니 수량입니다.
    private Long calculateTotalAmount(List<CartItem> cartItems) {
        return cartItems.stream()
                .mapToLong(cartItem -> (long) cartItem.getProduct().getPrice() * cartItem.getQuantity())
                .sum();
    }

    private CreateOrderResponse toResponse(Order order, Payment payment, List<OrderItem> orderItems) {
        return CreateOrderResponse.from(
                order,
                payment,
                orderItems
        );
    }
}
