package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.refund.dto.FullRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.PartialRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundItemRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundResponse;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;
import com.teamec2.paymentsystem.domain.refund.entity.RefundOutbox;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import com.teamec2.paymentsystem.domain.refund.repository.RefundItemRepository;
import com.teamec2.paymentsystem.domain.refund.repository.RefundOutboxRepository;
import com.teamec2.paymentsystem.domain.refund.repository.RefundRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final RefundOutboxRepository refundOutboxRepository;

    @Transactional
    public RefundResponse requestPartialRefund(
            Long userId,
            Long orderId,
            String idempotencyKey,
            PartialRefundRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        validatePartialRequest(request);

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        Order order = payment.getOrder();

        validateOrderOwner(order, userId);
        validateRefundablePayment(payment);

        Optional<Refund> existingRefund =
                refundRepository.findByPayment_IdAndIdempotencyKey(payment.getId(), idempotencyKey);

        if (existingRefund.isPresent()) {
            List<RefundItem> existingItems =
                    refundItemRepository.findAllByRefundIdWithOrderItem(existingRefund.get().getId());
            return RefundResponse.from(existingRefund.get(), existingItems);
        }

        validateNoActiveRefund(payment);

        List<OrderItem> orderItems = orderItemRepository.findWithProductByOrderId(order.getId());
        List<OrderItem> refundTargetItems = findPartialRefundItems(orderItems, request.items());

        Map<Long, Integer> quantityMap = toQuantityMap(request.items());
        long requestedRefundAmount = calculateRequestedRefundAmount(refundTargetItems, quantityMap);
        long totalRemainingRefundableAmount = calculateTotalRemainingRefundableAmount(orderItems);

        RefundAmount refundAmount = calculateRefundAmount(
                payment,
                requestedRefundAmount,
                totalRemainingRefundableAmount
        );

        return createRefundSnapshotAndOutbox(
                idempotencyKey,
                order,
                payment,
                request.reason(),
                refundTargetItems,
                quantityMap,
                refundAmount
        );
    }

    @Transactional
    public RefundResponse requestFullRefund(
            Long userId,
            Long paymentId,
            String idempotencyKey,
            FullRefundRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);

        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        Order order = payment.getOrder();

        validateOrderOwner(order, userId);
        validateRefundablePayment(payment);

        Optional<Refund> existingRefund =
                refundRepository.findByPayment_IdAndIdempotencyKey(payment.getId(), idempotencyKey);

        if (existingRefund.isPresent()) {
            List<RefundItem> existingItems =
                    refundItemRepository.findAllByRefundIdWithOrderItem(existingRefund.get().getId());
            return RefundResponse.from(existingRefund.get(), existingItems);
        }

        validateNoActiveRefund(payment);

        List<OrderItem> orderItems = orderItemRepository.findWithProductByOrderId(order.getId());

        List<OrderItem> refundTargetItems = orderItems.stream()
                .filter(orderItem -> orderItem.getRemainingRefundableQuantity() > 0)
                .toList();

        if (refundTargetItems.isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        Map<Long, Integer> quantityMap = new HashMap<>();
        for (OrderItem orderItem : refundTargetItems) {
            quantityMap.put(orderItem.getId(), orderItem.getRemainingRefundableQuantity());
        }

        long totalRemainingRefundableAmount = calculateTotalRemainingRefundableAmount(orderItems);

        RefundAmount refundAmount = calculateRefundAmount(
                payment,
                totalRemainingRefundableAmount,
                totalRemainingRefundableAmount
        );

        return createRefundSnapshotAndOutbox(
                idempotencyKey,
                order,
                payment,
                request.reason(),
                refundTargetItems,
                quantityMap,
                refundAmount
        );
    }

    private RefundResponse createRefundSnapshotAndOutbox(
            String idempotencyKey,
            Order order,
            Payment payment,
            String reason,
            List<OrderItem> refundTargetItems,
            Map<Long, Integer> quantityMap,
            RefundAmount refundAmount
    ) {
        Refund refund = refundRepository.save(Refund.createRefund(
                idempotencyKey,
                order,
                payment,
                reason,
                refundAmount.totalRefundAmount(),
                refundAmount.pointRefundAmount(),
                refundAmount.pgRefundAmount()
        ));

        List<RefundItem> refundItems = createRefundItems(refund, refundTargetItems, quantityMap, refundAmount);

        // 환불 요청이 생성되는 순간 수량을 예약합니다.
        // 그래야 같은 상품을 동시에 중복 환불하는 요청을 막을 수 있습니다.
        for (RefundItem refundItem : refundItems) {
            refundItem.getOrderItem().reserveRefundQuantity(refundItem.getRefundQuantity());
        }

        refundItemRepository.saveAll(refundItems);

        // 실제 PG 취소는 지금 하지 않고, 스케줄러가 처리할 작업만 저장합니다.
        refundOutboxRepository.save(RefundOutbox.create(refund, LocalDateTime.now()));

        return RefundResponse.from(refund, refundItems);
    }

    private List<RefundItem> createRefundItems(
            Refund refund,
            List<OrderItem> refundTargetItems,
            Map<Long, Integer> quantityMap,
            RefundAmount refundAmount
    ) {
        List<RefundItem> refundItems = new ArrayList<>();

        long remainingPointRefundAmount = refundAmount.pointRefundAmount();
        long remainingPgRefundAmount = refundAmount.pgRefundAmount();

        for (int i = 0; i < refundTargetItems.size(); i++) {
            OrderItem orderItem = refundTargetItems.get(i);
            int quantity = quantityMap.get(orderItem.getId());
            long itemRefundAmount = (long) orderItem.getPrice() * quantity;

            boolean lastItem = i == refundTargetItems.size() - 1;

            long itemPointRefundAmount;
            long itemPgRefundAmount;

            if (lastItem) {
                itemPointRefundAmount = remainingPointRefundAmount;
                itemPgRefundAmount = remainingPgRefundAmount;
            } else {
                itemPointRefundAmount =
                        itemRefundAmount * refundAmount.pointRefundAmount() / refundAmount.totalRefundAmount();
                itemPgRefundAmount = itemRefundAmount - itemPointRefundAmount;
            }

            remainingPointRefundAmount -= itemPointRefundAmount;
            remainingPgRefundAmount -= itemPgRefundAmount;

            refundItems.add(RefundItem.createRefundItem(
                    refund,
                    orderItem,
                    quantity,
                    itemPointRefundAmount,
                    itemPgRefundAmount
            ));
        }

        return refundItems;
    }

    private List<OrderItem> findPartialRefundItems(
            List<OrderItem> orderItems,
            List<RefundItemRequest> itemRequests
    ) {
        Map<Long, OrderItem> orderItemMap = new HashMap<>();

        for (OrderItem orderItem : orderItems) {
            orderItemMap.put(orderItem.getId(), orderItem);
        }

        Set<Long> requestedItemIds = new HashSet<>();
        List<OrderItem> refundTargetItems = new ArrayList<>();

        for (RefundItemRequest itemRequest : itemRequests) {
            if (!requestedItemIds.add(itemRequest.orderItemId())) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
            }

            OrderItem orderItem = orderItemMap.get(itemRequest.orderItemId());

            if (orderItem == null) {
                throw new BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND);
            }

            if (itemRequest.quantity() == null || itemRequest.quantity() <= 0) {
                throw new BusinessException(ErrorCode.INVALID_REFUND_QUANTITY);
            }

            if (itemRequest.quantity() > orderItem.getRemainingRefundableQuantity()) {
                throw new BusinessException(ErrorCode.REFUND_QUANTITY_EXCEEDED);
            }

            refundTargetItems.add(orderItem);
        }

        return refundTargetItems;
    }

    private Map<Long, Integer> toQuantityMap(List<RefundItemRequest> itemRequests) {
        Map<Long, Integer> quantityMap = new HashMap<>();

        for (RefundItemRequest itemRequest : itemRequests) {
            quantityMap.put(itemRequest.orderItemId(), itemRequest.quantity());
        }

        return quantityMap;
    }

    private long calculateRequestedRefundAmount(
            List<OrderItem> refundTargetItems,
            Map<Long, Integer> quantityMap
    ) {
        long refundAmount = 0L;

        for (OrderItem orderItem : refundTargetItems) {
            refundAmount += (long) orderItem.getPrice() * quantityMap.get(orderItem.getId());
        }

        if (refundAmount <= 0) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        return refundAmount;
    }

    private long calculateTotalRemainingRefundableAmount(List<OrderItem> orderItems) {
        long totalRemainingAmount = 0L;

        for (OrderItem orderItem : orderItems) {
            totalRemainingAmount +=
                    (long) orderItem.getPrice() * orderItem.getRemainingRefundableQuantity();
        }

        if (totalRemainingAmount <= 0) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        return totalRemainingAmount;
    }

    private RefundAmount calculateRefundAmount(
            Payment payment,
            long requestedRefundAmount,
            long totalRemainingRefundableAmount
    ) {
        boolean finalRefund = requestedRefundAmount == totalRemainingRefundableAmount;

        if (finalRefund) {
            long completedPointRefundAmount =
                    refundRepository.sumCompletedPointRefundAmount(payment.getId());
            long completedPgRefundAmount =
                    refundRepository.sumCompletedPgRefundAmount(payment.getId());

            long pointRefundAmount = payment.getUsedPointAmount() - completedPointRefundAmount;
            long pgRefundAmount = payment.getPgAmount() - completedPgRefundAmount;

            validateCalculatedAmount(requestedRefundAmount, pointRefundAmount, pgRefundAmount);

            return new RefundAmount(requestedRefundAmount, pointRefundAmount, pgRefundAmount);
        }

        long pointRefundAmount = calculatePointRefundAmount(payment, requestedRefundAmount);
        long pgRefundAmount = requestedRefundAmount - pointRefundAmount;

        validateCalculatedAmount(requestedRefundAmount, pointRefundAmount, pgRefundAmount);

        return new RefundAmount(requestedRefundAmount, pointRefundAmount, pgRefundAmount);
    }

    private long calculatePointRefundAmount(Payment payment, long refundAmount) {
        if (payment.getTotalAmount() == 0L || payment.getUsedPointAmount() == 0L) {
            return 0L;
        }

        return refundAmount * payment.getUsedPointAmount() / payment.getTotalAmount();
    }

    private void validateCalculatedAmount(
            long totalRefundAmount,
            long pointRefundAmount,
            long pgRefundAmount
    ) {
        if (totalRefundAmount <= 0 || pointRefundAmount < 0 || pgRefundAmount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (totalRefundAmount != pointRefundAmount + pgRefundAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void validateNoActiveRefund(Payment payment) {
        boolean exists = refundRepository.existsByPayment_IdAndStatusIn(
                payment.getId(),
                List.of(RefundStatus.PROCESSING, RefundStatus.PG_RESULT_UNKNOWN)
        );

        if (exists) {
            throw new BusinessException(ErrorCode.REFUND_IN_PROGRESS);
        }
    }

    private void validateRefundablePayment(Payment payment) {
        switch (payment.getStatus()) {
            case COMPLETED, PARTIAL_REFUNDED -> {
            }
            default -> throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }
    }

    private void validateOrderOwner(Order order, Long userId) {
        if (!Objects.equals(order.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    private void validatePartialRequest(PartialRefundRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_ITEM_REQUIRED);
        }
    }

    private record RefundAmount(
            long totalRefundAmount,
            long pointRefundAmount,
            long pgRefundAmount
    ) {
    }
}