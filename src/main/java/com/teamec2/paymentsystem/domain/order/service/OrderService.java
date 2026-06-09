package com.teamec2.paymentsystem.domain.order.service;

import com.teamec2.paymentsystem.domain.cart.entity.CartItem;
import com.teamec2.paymentsystem.domain.order.dto.CancelItemRequest;
import com.teamec2.paymentsystem.domain.order.dto.CancelRequest;
import com.teamec2.paymentsystem.domain.order.dto.CancelResponse;
import com.teamec2.paymentsystem.domain.order.dto.CreateResponse;
import com.teamec2.paymentsystem.domain.order.dto.OrderDetailResponse;
import com.teamec2.paymentsystem.domain.order.dto.OrderListResponse;
import com.teamec2.paymentsystem.domain.order.dto.OrderPreviewResponse;
import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.order.facade.OrderCartFacade;
import com.teamec2.paymentsystem.domain.order.facade.OrderPaymentFacade;
import com.teamec2.paymentsystem.domain.order.facade.OrderProductFacade;
import com.teamec2.paymentsystem.domain.order.facade.OrderProductTarget;
import com.teamec2.paymentsystem.domain.order.facade.OrderUserFacade;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.service.PointPolicy;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderUserFacade orderUserFacade;
    private final OrderCartFacade orderCartFacade;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderPaymentFacade orderPaymentFacade;
    private final OrderNumberGenerator orderNumberGenerator;
    private final PointPolicy pointPolicy;
    private final PointService pointService;
    private final OrderProductFacade orderProductFacade;

    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(Long userId, List<Long> cartItemIds) {
        List<CartItem> cartItems = orderCartFacade.getCartItems(userId, cartItemIds);
        orderProductFacade.validateCartProducts(cartItems);

        return OrderPreviewResponse.from(cartItems);
    }

    // 주문 생성, 재고 선차감, 결제 대기 생성은 하나의 작업처럼 성공하거나 실패해야 합니다.
    // 그래서 중간에 재고 부족 같은 예외가 발생하면 전체 DB 변경이 롤백되도록 @Transactional을 사용합니다.
    @Transactional
    public CreateResponse createOrder(Long userId, List<Long> cartItemIds, Long usedPointAmount) {
        if (usedPointAmount == null || usedPointAmount < 0) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }

        User user = orderUserFacade.getUser(userId);

        if (user.getPointBalance() < usedPointAmount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINT);
        }

        // 주문 생성에서는 CartItem만 먼저 조회합니다.
        // Product를 이 시점에 함께 가져오면, 뒤에서 상품 row 락을 기다린 뒤에도 이미 로딩된 오래된 Product 값을 볼 수 있습니다.
        List<CartItem> cartItems = orderCartFacade.getCartItemsWithLock(userId, cartItemIds);

        // 같은 상품을 여러 회원이 동시에 주문하면 둘 다 같은 재고를 보고 차감할 수 있습니다.
        // 그래서 실제 금액 계산과 재고 차감 전에 주문 대상 상품 row를 먼저 잠급니다.
        // lockOrderProducts가 반환한 OrderProductTarget에는 락을 얻은 뒤 조회된 Product가 들어 있습니다.
        List<OrderProductTarget> orderTargets = orderProductFacade.lockOrderProducts(cartItems);
        orderProductFacade.validateOrderProducts(orderTargets);

        // 주문 총액은 락을 얻은 Product의 가격과 장바구니 수량으로 계산합니다.
        Long totalAmount = sumOrderTargets(orderTargets);

        if (usedPointAmount > totalAmount) {
            throw new BusinessException(ErrorCode.INVALID_USED_POINT);
        }

        orderProductFacade.decreaseStocks(orderTargets);

        // 실제 포인트 적립이 아닌 적립 예정 포인트 계산입니다.
        // pgAmount는 PG 결제창에서 실제 카드/간편결제로 결제해야 하는 금액입니다.
        // totalAmount 전체 금액에서 사용 포인트를 뺀 값입니다.
        Long pgAmount = totalAmount - usedPointAmount;

        // rewardPointAmount는 결제 완료 후 적립될 예정 포인트입니다.
        // 현재 정책은 PointPolicy가 계산하고, 실제 적립은 결제 확정 시점에 수행됩니다.
        Long rewardPointAmount = pointPolicy.calculateRewardPoint(pgAmount);

        // 주문은 처음에는 결제대기 상태로 생성됩니다.
        Order order = Order.create(
                user,
                orderNumberGenerator.generate(),
                totalAmount,
                usedPointAmount
        );

        Order savedOrder = orderRepository.save(order);

        // 주문 상품에는 현재 상품명과 가격이 스냅샷으로 저장됩니다.
        // 상품명이 나중에 바뀌어도 이 주문의 상품명은 주문 당시 값으로 남습니다.
        // sourceCartItemId도 함께 저장해 결제 완료 시 이번 주문에 포함된 장바구니 항목만 지울 수 있게 합니다.
        List<OrderItem> orderItems = createOrderItems(savedOrder, orderTargets);

        List<OrderItem> savedOrderItems = orderItemRepository.saveAll(orderItems);

        // PortOne에 전달할 portonePaymentId는 Payment.createPending 내부에서 미리 생성됩니다.
        // 아직 실제 결제가 끝난 것이 아니므로 결제 상태는 PENDING입니다.
        Payment savedPayment = orderPaymentFacade.savePendingPayment(
                savedOrder,
                totalAmount,
                usedPointAmount,
                pgAmount,
                rewardPointAmount
        );

        // 결제 대기 상태에서 사용할 포인트를 예약 차감합니다.
        // 예약에 실패하면 @Transactional 때문에 주문/결제/재고 변경도 함께 롤백됩니다.
        pointService.reserveUsedPoints(savedPayment);

        return CreateResponse.from(
                savedOrder,
                savedPayment,
                savedOrderItems
        );
    }

    @Transactional(readOnly = true)
    public OrderListResponse findMyOrders(Long userId) {
        List<Order> orders = orderRepository.findLatestByUserId(userId);

        return OrderListResponse.from(orders);
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse findMyOrderDetail(Long userId, Long orderId) {
        Order order = findOrder(orderId);

        validateOrderOwner(order, userId);

        List<OrderItem> orderItems = orderItemRepository.findWithProductByOrderId(orderId);
        Payment payment = orderPaymentFacade.getPayment(orderId);

        return OrderDetailResponse.from(order, orderItems, payment);
    }

    @Transactional
    public CancelResponse cancelOrder(Long userId, Long orderId, List<Long> orderItemIds) {
        return cancelOrderByRequest(userId, orderId, CancelRequest.fromOrderItemIds(orderItemIds));
    }

    @Transactional
    public CancelResponse cancelOrderByRequest(Long userId, Long orderId, CancelRequest request) {
        // request.items는 취소하려는 주문상품 ID와 수량 목록입니다.
        // 기존 orderItemIds 요청은 하위 호환을 위해 주문상품의 남은 수량 전체 취소로 처리합니다.
        // 본문이 없으면 "아직 취소되지 않은 모든 주문상품의 남은 수량 전체 취소"로 처리합니다.
        User user = orderUserFacade.getUser(userId);
        Order order = findOrder(orderId);
        validateOrderOwner(order, user.getId());

        Payment payment = orderPaymentFacade.getPaymentWithLock(orderId);
        validateCancelable(order, payment);

        List<OrderItem> allOrderItems = findAllOrderItemsForCancel(orderId);
        List<CancelTarget> cancelTargets = selectCancelTargets(allOrderItems, request);

        if (cancelTargets.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        // previousOrderStatus는 응답에서 "취소 전 주문 상태"를 보여주기 위해 저장합니다.
        OrderStatus previousOrderStatus = order.getStatus();

        // canceledAmount는 이번 요청으로 취소되는 주문상품 수량들의 금액 합계입니다.
        Long canceledAmount = sumCancelAmount(cancelTargets);

        // remainingTotalAmount는 이번 취소 후 주문에 남는 상품 금액입니다.
        // 0이면 전체 취소, 0보다 크면 부분 취소입니다.
        Long remainingTotalAmount = sumRemainingAmount(allOrderItems, canceledAmount);

        // cancelPointKeys는 포인트 원장 멱등 키를 만들 때 사용합니다.
        // 같은 주문상품을 수량 단위로 여러 번 취소할 수 있어 취소 전 수량과 취소 수량도 함께 반영합니다.
        List<String> cancelPointKeys = toCancelPointKeys(cancelTargets);

        // restoredStockItems는 응답에 담을 "재고가 몇 개 복구됐는지" 정보입니다.
        List<CancelResponse.RestoredStockItem> restoredStockItems = toRestoredStocks(cancelTargets);

        CancelAmounts cancelAmounts = calculateCancelAmounts(payment, remainingTotalAmount);
        updateCancelAmounts(order, payment, remainingTotalAmount, cancelAmounts, cancelPointKeys);

        // 재고 복구 전에 취소 대상 상품 row를 쓰기 잠금으로 조회합니다.
        // 주문 생성의 재고 차감과 동시에 실행되어도 최신 재고 값을 기준으로 복구하기 위해서입니다.
        Map<Long, Product> lockedProducts = lockProductsForStockRestore(cancelTargets);

        // 주문상품 취소 처리는 상품 재고 복구와 주문상품 상태 변경을 함께 수행합니다.
        for (CancelTarget cancelTarget : cancelTargets) {
            OrderItem cancelOrderItem = cancelTarget.orderItem();
            cancelOrderItem.cancel(
                    cancelTarget.quantity(),
                    lockedProducts.get(cancelOrderItem.getProductId())
            );
        }

        updateCancelStatus(order, payment, remainingTotalAmount);

        return new CancelResponse(
                order.getId(),
                order.getOrderNumber(),
                previousOrderStatus,
                order.getStatus(),
                canceledAmount,
                remainingTotalAmount,
                cancelAmounts.restoredPointAmount(),
                cancelAmounts.remainingUsedPointAmount(),
                cancelAmounts.remainingPgAmount(),
                payment.getStatus(),
                restoredStockItems,
                LocalDateTime.now()
        );
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateOrderOwner(Order order, Long userId) {
        if (!order.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    // 이미 결제가 끝난 주문은 주문 취소가 아니라 PG 환불 흐름으로 처리해야 합니다.
    private void validateCancelable(Order order, Payment payment) {
        if (!payment.isPending() || !order.canConfirmPayment()) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
        }
    }

    private Long sumOrderTargets(List<OrderProductTarget> orderTargets) {
        return orderTargets.stream()
                .mapToLong(orderTarget -> (long) orderTarget.product().getPrice() * orderTarget.cartItem().getQuantity())
                .sum();
    }

    private List<OrderItem> createOrderItems(Order order, List<OrderProductTarget> orderTargets) {
        return orderTargets.stream()
                .map(orderTarget -> new OrderItem(
                        order,
                        orderTarget.product(),
                        orderTarget.cartItem().getId(),
                        orderTarget.cartItem().getQuantity()
                ))
                .toList();
    }

    private List<OrderItem> findAllOrderItemsForCancel(Long orderId) {
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        if (orderItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        return orderItems;
    }

    private List<CancelTarget> selectCancelTargets(List<OrderItem> allOrderItems, CancelRequest request) {
        if (request != null && request.items() != null && !request.items().isEmpty()) {
            return selectCancelTargetsByQuantities(allOrderItems, request.items());
        }

        List<Long> orderItemIds = request == null ? null : request.orderItemIds();
        return selectCancelTargetsByOrderItemIds(allOrderItems, orderItemIds);
    }

    private List<CancelTarget> selectCancelTargetsByQuantities(
            List<OrderItem> allOrderItems,
            List<CancelItemRequest> itemRequests
    ) {
        Map<Long, OrderItem> orderItemMap = allOrderItems.stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));

        Set<Long> requestedOrderItemIds = new HashSet<>();
        List<CancelTarget> cancelTargets = new ArrayList<>();

        for (CancelItemRequest itemRequest : itemRequests) {
            if (itemRequest.orderItemId() == null || itemRequest.quantity() == null) {
                throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
            }

            if (!requestedOrderItemIds.add(itemRequest.orderItemId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
            }

            OrderItem orderItem = orderItemMap.get(itemRequest.orderItemId());
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }

            validateCancelTarget(orderItem, itemRequest.quantity());
            cancelTargets.add(new CancelTarget(orderItem, itemRequest.quantity(), orderItem.getQuantity()));
        }

        return cancelTargets;
    }

    private List<CancelTarget> selectCancelTargetsByOrderItemIds(
            List<OrderItem> allOrderItems,
            List<Long> orderItemIds
    ) {
        List<Long> distinctOrderItemIds = distinctIds(orderItemIds);

        if (distinctOrderItemIds == null || distinctOrderItemIds.isEmpty()) {
            return allOrderItems.stream()
                    .filter(orderItem -> !orderItem.isCanceled())
                    .map(orderItem -> new CancelTarget(orderItem, orderItem.getQuantity(), orderItem.getQuantity()))
                    .toList();
        }

        List<OrderItem> cancelOrderItems = allOrderItems.stream()
                .filter(orderItem -> distinctOrderItemIds.contains(orderItem.getId()))
                .toList();

        if (cancelOrderItems.size() != distinctOrderItemIds.size()) {
            throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
        }

        return cancelOrderItems.stream()
                .peek(orderItem -> validateCancelTarget(orderItem, orderItem.getQuantity()))
                .map(orderItem -> new CancelTarget(orderItem, orderItem.getQuantity(), orderItem.getQuantity()))
                .toList();
    }

    private void validateCancelTarget(OrderItem orderItem, int cancelQuantity) {
        if (orderItem.isCanceled()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (cancelQuantity < 1) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_QUANTITY);
        }

        if (cancelQuantity > orderItem.getQuantity()) {
            throw new BusinessException(ErrorCode.ORDER_CANCEL_QUANTITY_EXCEEDED);
        }
    }

    private Long sumCancelAmount(List<CancelTarget> cancelTargets) {
        return cancelTargets.stream()
                .mapToLong(cancelTarget -> (long) cancelTarget.orderItem().getPrice() * cancelTarget.quantity())
                .sum();
    }

    private Long sumRemainingAmount(List<OrderItem> allOrderItems, Long canceledAmount) {
        Long currentTotalAmount = allOrderItems.stream()
                .filter(orderItem -> !orderItem.isCanceled())
                .mapToLong(OrderItem::getSubtotal)
                .sum();

        return currentTotalAmount - canceledAmount;
    }

    private CancelAmounts calculateCancelAmounts(Payment payment, Long remainingTotalAmount) {
        Long previousUsedPointAmount = payment.getUsedPointAmount();

        // 사용 포인트가 남은 주문 금액보다 커질 수 없으므로 둘 중 작은 값을 사용합니다.
        Long remainingUsedPointAmount = Math.min(previousUsedPointAmount, remainingTotalAmount);
        Long restoredPointAmount = previousUsedPointAmount - remainingUsedPointAmount;
        Long remainingPgAmount = remainingTotalAmount - remainingUsedPointAmount;
        Long rewardPointAmount = pointPolicy.calculateRewardPoint(remainingPgAmount);

        return new CancelAmounts(
                restoredPointAmount,
                remainingUsedPointAmount,
                remainingPgAmount,
                rewardPointAmount
        );
    }

    private void updateCancelAmounts(
            Order order,
            Payment payment,
            Long remainingTotalAmount,
            CancelAmounts cancelAmounts,
            List<String> cancelPointKeys
    ) {
        if (remainingTotalAmount == 0L) {
            pointService.cancelReservedPoints(payment);
            return;
        }

        // 남은 주문 금액이 있으면 부분 취소입니다.
        // 줄어든 사용 포인트만 일부 복구하고, 주문/결제 금액을 남은 금액 기준으로 갱신합니다.
        pointService.restoreReservedPointsForOrderCancel(
                payment,
                cancelAmounts.restoredPointAmount(),
                cancelPointKeys
        );
        payment.updatePendingAmounts(
                remainingTotalAmount,
                cancelAmounts.remainingUsedPointAmount(),
                cancelAmounts.remainingPgAmount(),
                cancelAmounts.rewardPointAmount()
        );
        order.updateAmounts(remainingTotalAmount, cancelAmounts.remainingUsedPointAmount());
    }

    private void updateCancelStatus(Order order, Payment payment, Long remainingTotalAmount) {
        if (remainingTotalAmount == 0L) {
            // 전체 취소이면 주문은 CANCELED, 결제는 FAILED로 정리합니다.
            order.cancelBeforePayment();
            payment.fail(LocalDateTime.now());
            return;
        }

        // 일부 상품만 취소했으면 주문은 PARTIAL_CANCELED로 남깁니다.
        order.partialCancel();
    }

    private List<String> toCancelPointKeys(List<CancelTarget> cancelTargets) {
        return cancelTargets.stream()
                .map(cancelTarget -> "%d:%d:%d".formatted(
                        cancelTarget.orderItem().getId(),
                        cancelTarget.quantityBeforeCancel(),
                        cancelTarget.quantity()
                ))
                .toList();
    }

    private List<CancelResponse.RestoredStockItem> toRestoredStocks(List<CancelTarget> cancelTargets) {
        return cancelTargets.stream()
                .map(cancelTarget -> new CancelResponse.RestoredStockItem(
                        cancelTarget.orderItem().getId(),
                        cancelTarget.orderItem().getProductId(),
                        cancelTarget.quantity()
                ))
                .toList();
    }

    private Map<Long, Product> lockProductsForStockRestore(List<CancelTarget> cancelTargets) {
        List<Long> productIds = cancelTargets.stream()
                .map(cancelTarget -> cancelTarget.orderItem().getProductId())
                .toList();

        return orderProductFacade.lockProducts(productIds);
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ids;
        }

        return new LinkedHashSet<>(ids).stream()
                .toList();
    }

    private record CancelTarget(
            OrderItem orderItem,
            int quantity,
            int quantityBeforeCancel
    ) {
    }

    private record CancelAmounts(
            Long restoredPointAmount,
            Long remainingUsedPointAmount,
            Long remainingPgAmount,
            Long rewardPointAmount
    ) {
    }
}
