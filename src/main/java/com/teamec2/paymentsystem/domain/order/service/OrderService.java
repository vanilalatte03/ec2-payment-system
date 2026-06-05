package com.teamec2.paymentsystem.domain.order.service;

import com.teamec2.paymentsystem.domain.cart.entity.Cart;
import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.cart.repository.CartItemRepository;
import com.teamec2.paymentsystem.domain.cart.repository.CartRepository;
import com.teamec2.paymentsystem.domain.order.dto.CancelOrderResponse;
import com.teamec2.paymentsystem.domain.order.dto.CreateOrderResponse;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.service.PointPolicy;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {
    // PortOne에 결제 취소를 요청할 때 남기는 취소 사유입니다.
    // "외부 결제는 성공했지만 우리 서버의 결제 확정 전 주문 취소가 들어온 상황"을 구분하기 위한 값입니다.
    private static final String ORDER_CANCEL_REASON = "ORDER_CANCEL_BEFORE_INTERNAL_CONFIRM";

    // 같은 결제 취소 요청이 여러 번 전달되어도 PortOne이 중복 취소로 처리하지 않도록 만드는 멱등 키 접두어입니다.
    // 최종 키는 order-cancel-{paymentId} 형태가 됩니다.
    private static final String ORDER_CANCEL_IDEMPOTENCY_KEY_PREFIX = "order-cancel-";

    private final UserRepository userRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final PointPolicy pointPolicy;
    private final PointService pointService;
    private final ProductRepository productRepository;
    private final PaymentGateway paymentGateway;

    // 주문 생성, 재고 선차감, 결제 대기 생성은 하나의 작업처럼 성공하거나 실패해야 합니다.
    // 그래서 중간에 재고 부족 같은 예외가 발생하면 전체 DB 변경이 롤백되도록 @Transactional을 사용합니다.
    @Transactional
    public CreateOrderResponse createOrder(Long userId, List<Long> cartItemIds, Long usePointAmount) {
        if (usePointAmount == null || usePointAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }

        List<Long> distinctCartItemIds = cartItemIds;
        if (cartItemIds != null && !cartItemIds.isEmpty()) {
            distinctCartItemIds = new LinkedHashSet<>(cartItemIds).stream()
                    .toList();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getPointBalance() < usePointAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }

        Cart cart = cartRepository.findByUserIdWithOptimisticLock(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_EMPTY));

        // cartItemIds가 있으면 선택 상품만, 없으면 장바구니 전체 상품을 주문 대상으로 가져옵니다.
        List<CartItem> cartItems;
        if (distinctCartItemIds == null || distinctCartItemIds.isEmpty()) {
            cartItems = cartItemRepository.findWithProductByCartId(cart.getId());
        } else {
            cartItems = cartItemRepository.findOrderItemsByCartIdAndIdIn(cart.getId(), distinctCartItemIds);
        }

        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.CART_EMPTY);
        }

        if (distinctCartItemIds != null && !distinctCartItemIds.isEmpty() && cartItems.size() != distinctCartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 같은 상품을 여러 회원이 동시에 주문하면 둘 다 같은 재고를 보고 차감할 수 있습니다.
        // 그래서 실제 금액 계산과 재고 차감 전에 주문 대상 상품 row를 먼저 잠급니다.
        lockOrderTargetProducts(cartItems);

        // 주문 총액은 주문 생성 순간의 상품 가격과 장바구니 수량으로 계산합니다.
        Long totalAmount = cartItems.stream()
                .mapToLong(cartItem -> (long) cartItem.getProduct().getPrice() * cartItem.getQuantity())
                .sum();

        if (usePointAmount > totalAmount) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }

        // 결제 완료 시점이 아니라 주문 생성 시점에 재고를 먼저 차감합니다.
        // 이 반복문 중 하나라도 실패하면 @Transactional 때문에 앞선 차감도 함께 롤백됩니다.
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();

            try {
                product.decreaseStock(cartItem.getQuantity());
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == ErrorCode.PRODUCT_OUT_OF_STOCK) {
                    throw new BusinessException(ErrorCode.ORDER_STOCK_SHORTAGE);
                }

                throw exception;
            }
        }

        // 실제 포인트 적립이 아닌 적립 예정 포인트 계산입니다.
        // pgAmount는 PG 결제창에서 실제 카드/간편결제로 결제해야 하는 금액입니다.
        // totalAmount 전체 금액에서 사용 포인트를 뺀 값입니다.
        Long pgAmount = totalAmount - usePointAmount;

        // rewardPointAmount는 결제 완료 후 적립될 예정 포인트입니다.
        // 현재 정책은 PointPolicy가 계산하고, 실제 적립은 결제 확정 시점에 수행됩니다.
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
        // sourceCartItemId도 함께 저장해 결제 완료 시 이번 주문에 포함된 장바구니 항목만 지울 수 있게 합니다.
        List<OrderItem> orderItems = cartItems.stream()
                .map(cartItem -> new OrderItem(
                        savedOrder,
                        cartItem.getProduct(),
                        cartItem.getId(),
                        cartItem.getQuantity()
                ))
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

        return CreateOrderResponse.from(
                savedOrder,
                savedPayment,
                savedOrderItems
        );
    }

    @Transactional
    public CancelOrderResponse cancelOrder(Long userId, Long orderId, List<Long> orderItemIds) {
        // orderItemIds는 취소하려는 주문상품 ID 목록입니다.
        // null 또는 빈 목록이면 "아직 취소되지 않은 모든 주문상품 취소", 값이 있으면 "선택한 주문상품만 취소"로 처리합니다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        // 로그인한 고객의 주문이 맞는지 확인합니다.
        if (!order.getUser().getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // 결제가 아직 대기 중이고, 주문도 결제 전 취소 가능한 상태일 때만 직접 취소합니다.
        // 이미 결제가 끝난 주문은 주문 취소가 아니라 PG 환불 흐름으로 처리해야 합니다.
        // cancelableOrderStatus는 이 주문이 회원 직접 취소 대상 상태인지 나타내는 값입니다.
        boolean cancelableOrderStatus = order.getStatus() == OrderStatus.PAYMENT_PENDING
                || order.getStatus() == OrderStatus.PARTIAL_CANCELED;

        if (!payment.isPending() || !cancelableOrderStatus) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }

        List<OrderItem> allOrderItems = orderItemRepository.findAllWithProductByOrderId(orderId);
        if (allOrderItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        // distinctOrderItemIds는 취소 요청으로 들어온 주문상품 ID에서 중복을 제거한 목록입니다.
        // 같은 주문상품 ID가 여러 번 들어와도 취소 금액과 재고 복구가 중복 계산되지 않게 합니다.
        List<Long> distinctOrderItemIds = orderItemIds == null || orderItemIds.isEmpty()
                ? orderItemIds
                : new LinkedHashSet<>(orderItemIds).stream()
                        .toList();

        // cancelOrderItems는 이번 요청에서 실제로 취소할 주문상품 목록입니다.
        List<OrderItem> cancelOrderItems;
        if (distinctOrderItemIds == null || distinctOrderItemIds.isEmpty()) {
            // 요청에 주문상품 ID가 없으면 아직 취소되지 않은 모든 주문상품을 취소합니다.
            cancelOrderItems = allOrderItems.stream()
                    .filter(orderItem -> !orderItem.isCanceled())
                    .toList();
        } else {
            cancelOrderItems = allOrderItems.stream()
                    .filter(orderItem -> distinctOrderItemIds.contains(orderItem.getId()))
                    .toList();

            if (cancelOrderItems.size() != distinctOrderItemIds.size()) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }

            if (cancelOrderItems.stream().anyMatch(OrderItem::isCanceled)) {
                throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
            }
        }

        if (cancelOrderItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        // previousOrderStatus는 응답에서 "취소 전 주문 상태"를 보여주기 위해 저장합니다.
        OrderStatus previousOrderStatus = order.getStatus();

        // canceledAmount는 이번 요청으로 취소되는 주문상품들의 금액 합계입니다.
        Long canceledAmount = cancelOrderItems.stream()
                .mapToLong(OrderItem::getSubtotal)
                .sum();

        // remainingTotalAmount는 이번 취소 후 주문에 남는 상품 금액입니다.
        // 0이면 전체 취소, 0보다 크면 부분 취소입니다.
        Long remainingTotalAmount = allOrderItems.stream()
                .filter(orderItem -> !orderItem.isCanceled())
                .filter(orderItem -> !cancelOrderItems.contains(orderItem))
                .mapToLong(OrderItem::getSubtotal)
                .sum();

        cancelExternalPaymentIfAlreadyPaid(payment, remainingTotalAmount);

        Long previousUsedPointAmount = payment.getUsedPointAmount();

        // 사용 포인트가 남은 주문 금액보다 커질 수 없으므로 둘 중 작은 값을 사용합니다.
        Long remainingUsedPointAmount = Math.min(previousUsedPointAmount, remainingTotalAmount);
        Long restoredPointAmount = previousUsedPointAmount - remainingUsedPointAmount;
        Long remainingPgAmount = remainingTotalAmount - remainingUsedPointAmount;
        Long rewardPointAmount = pointPolicy.calculateRewardPoint(remainingPgAmount);

        // cancelOrderItemIds는 포인트 원장 멱등 키를 만들 때 사용합니다.
        // 어떤 주문상품 취소 때문에 포인트가 복구되었는지 구분하기 위한 목록입니다.
        List<Long> cancelOrderItemIds = cancelOrderItems.stream()
                .map(OrderItem::getId)
                .toList();

        // restoredStockItems는 응답에 담을 "재고가 몇 개 복구됐는지" 정보입니다.
        List<CancelOrderResponse.RestoredStockItem> restoredStockItems = cancelOrderItems.stream()
                .map(orderItem -> new CancelOrderResponse.RestoredStockItem(
                        orderItem.getId(),
                        orderItem.getProductId(),
                        orderItem.getQuantity()
                ))
                .toList();

        if (remainingTotalAmount == 0L) {
            pointService.cancelReservedPoints(payment);
        } else {
            // 남은 주문 금액이 있으면 부분 취소입니다.
            // 줄어든 사용 포인트만 일부 복구하고, 주문/결제 금액을 남은 금액 기준으로 갱신합니다.
            pointService.restoreReservedPointsForOrderCancel(payment, restoredPointAmount, cancelOrderItemIds);
            payment.updatePendingAmounts(
                    remainingTotalAmount,
                    remainingUsedPointAmount,
                    remainingPgAmount,
                    rewardPointAmount
            );
            order.updateAmounts(remainingTotalAmount, remainingUsedPointAmount);
        }

        // 주문상품 취소 처리는 상품 재고 복구와 주문상품 상태 변경을 함께 수행합니다.
        cancelOrderItems.forEach(OrderItem::cancel);

        if (remainingTotalAmount == 0L) {
            // 전체 취소이면 주문은 CANCELED, 결제는 FAILED로 정리합니다.
            order.cancelPendingPayment();
            payment.fail(LocalDateTime.now());
        } else {
            // 일부 상품만 취소했으면 주문은 PARTIAL_CANCELED로 남깁니다.
            order.changeToPartialCanceled();
        }

        return new CancelOrderResponse(
                order.getId(),
                order.getOrderNumber(),
                previousOrderStatus,
                order.getStatus(),
                canceledAmount,
                remainingTotalAmount,
                restoredPointAmount,
                remainingUsedPointAmount,
                remainingPgAmount,
                payment.getStatus(),
                restoredStockItems,
                LocalDateTime.now()
        );
    }

    // 주문하려는 상품 row를 상품 ID 오름차순으로 잠급니다.
    // 여러 주문이 같은 상품 재고를 동시에 차감할 때 재고가 음수가 되는 문제를 막기 위한 메서드입니다.
    private void lockOrderTargetProducts(List<CartItem> cartItems) {
        // productIds는 주문 대상 장바구니 상품들에서 뽑은 실제 상품 ID 목록입니다.
        List<Long> productIds = cartItems.stream()
                .map(cartItem -> cartItem.getProduct().getId())
                .distinct()
                // 여러 상품을 한 주문에 담았을 때 항상 같은 순서로 잠그면 교착상태 위험을 줄일 수 있습니다.
                .sorted()
                .toList();

        productRepository.findAllByIdInForUpdate(productIds);
    }

    // 우리 DB에는 결제가 아직 PENDING이어도, PortOne에서는 이미 PAID일 수 있습니다.
    // 이 경우 내부 주문만 취소하면 외부 결제가 남기 때문에, 먼저 PortOne 취소를 성공시킨 뒤 내부 상태를 정리합니다.
    private void cancelExternalPaymentIfAlreadyPaid(Payment payment, Long remainingTotalAmount) {
        // pgAmount가 0이면 포인트 전액 결제입니다.
        // 외부 PG 결제가 없으므로 PortOne 조회/취소가 필요 없습니다.
        if (payment.getPgAmount() == 0L) {
            return;
        }

        // gatewayResponse는 PortOne에서 조회한 실제 결제 상태입니다.
        PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(payment.getPortonePaymentId());
        validateGatewayPaymentForOrderCancel(payment, gatewayResponse);

        // 아직 PortOne에서도 PAID가 아니면 외부 취소 없이 기존 예약 주문 취소만 진행합니다.
        if (!gatewayResponse.isPaid()) {
            return;
        }

        // 현재 부분 환불 서비스가 없기 때문에, 이미 PAID인 결제를 부분 취소하면 위험합니다.
        // 그래서 전액 취소가 아닌 경우에는 PG 취소로 넘어가지 않고 막습니다.
        if (remainingTotalAmount > 0L) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        // PortOne 결제가 이미 성공했다면 외부 결제부터 취소합니다.
        // 이 취소가 성공해야만 아래 내부 주문/결제 상태 정리가 이어집니다.
        PaymentCancelResponse cancelResponse = paymentGateway.cancelPayment(
                payment.getPortonePaymentId(),
                gatewayResponse.paidAmount(),
                ORDER_CANCEL_REASON,
                ORDER_CANCEL_IDEMPOTENCY_KEY_PREFIX + payment.getId()
        );

        if (!cancelResponse.isSucceeded()) {
            throw new BusinessException(ErrorCode.REFUND_PG_CANCEL_FAILED);
        }
    }

    // 주문 취소 전에 PortOne 조회 결과가 우리 결제 정보와 맞는지 검증합니다.
    // 외부 결제 ID가 다르거나, PAID인데 결제 금액이 없으면 내부 취소를 진행하면 안 됩니다.
    private void validateGatewayPaymentForOrderCancel(Payment payment, PaymentGatewayResponse gatewayResponse) {
        if (gatewayResponse == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }

        if (!gatewayResponse.hasSamePaymentId(payment.getPortonePaymentId())) {
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ID_MISMATCH);
        }

        if (gatewayResponse.isPaid() && gatewayResponse.paidAmount() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }
    }
}
