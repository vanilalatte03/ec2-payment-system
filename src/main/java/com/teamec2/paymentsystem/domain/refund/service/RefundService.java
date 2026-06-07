package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 결제 조회
 * 주문 소유자 검증
 * 환불 가능 여부 검증
 * 환불 금액 계산 요청
 * Refund 스냅샷 저장
 * RefundItem 저장
 * RefundOutbox 저장
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final PaymentRepository paymentRepository;
    private final OrderItemRepository orderItemRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final RefundOutboxRepository refundOutboxRepository;
    private final RefundPointSettlementCalculator refundPointSettlementCalculator;
    private final PointService pointService;


    /**
     * 부분 환불 요청을 처리합니다.
     * 처리 흐름:
     * 1. 멱등키와 요청값을 검증합니다.
     * 2. 주문 ID 기준으로 Payment를 비관적 락으로 조회합니다.
     * 3. 주문 소유권을 검증합니다.
     * 4. 결제 상태가 환불 가능한 상태인지 검증합니다.
     * 5. 같은 멱등키로 이미 생성된 환불이 있으면 기존 결과를 반환합니다.
     * 6. 진행 중인 환불이 있는지 검증합니다.
     * 7. 환불 대상 상품과 수량을 검증합니다.
     * 8. 환불 금액과 포인트 정산 금액을 계산합니다.
     * 9. Refund/RefundItem/RefundOutbox 스냅샷을 저장합니다.
     */
    @Transactional
    public RefundResponse requestPartialRefund(
            Long userId,
            Long orderId,
            String idempotencyKey,
            PartialRefundRequest request
    ) {
        validateIdempotencyKey(idempotencyKey);
        validatePartialRequest(request);

        String requestHash = createPartialRefundRequestHash(request);

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        Order order = payment.getOrder();

        validateOrderOwner(order, userId);
        validateRefundablePayment(payment);

        /**
         * 멱등성 처리
         * 같은 paymentId + idempotencyKey로 이미 만들어진 환불 요청이 있다면 새로 생성하지 않고 기존 환불 응답을 반환합니다.
         * 네트워크 재시도, 사용자의 중복 클릭, 클라이언트 재전송 상황에서 같은 환불이 중복 생성되는 것을 막습니다.
         */
        Optional<Refund> existingRefund =
                refundRepository.findByPayment_IdAndIdempotencyKey(payment.getId(), idempotencyKey);

        if (existingRefund.isPresent()) {
            validateSameIdempotentRequest(existingRefund.get(), requestHash);

            List<RefundItem> existingItems =
                    refundItemRepository.findAllByRefundIdWithOrderItem(existingRefund.get().getId());
            return RefundResponse.from(existingRefund.get(), existingItems);
        }

        validateNoActiveRefund(payment);

        /**
         * 주문 상품을 상품 정보와 함께 조회하여 추후 환불 대상 상품 검증, 환불 가능 수량 금증, 환불 금액 계산에 사용합니다.
         */
        List<OrderItem> orderItems = orderItemRepository.findAllWithProductByOrderId(order.getId());

        /**
         * 요청으로 들어온 orderItemId와 quantity가 실제 환불 가능한지 검증하고, 환불 대상 OrderItem 목록을 만듭니다.
         */
        List<OrderItem> refundTargetItems = findPartialRefundItems(orderItems, request.items());

        /**
         * orderItemId -> 환불 수량 형태로 변환합니다.
         * RefundItem 생성과 환불 금액 계산에서 사용합니다.
         */
        Map<Long, Integer> quantityMap = toQuantityMap(request.items());


        /**
         * 이번 요청에서 환불하려는 상품들의 총 금액입니다.
         * ex)
         * A 상품 10,000원 x 1개
         * B 상품 5,000원 x 2개
         * → requestedRefundAmount = 20,000원
         */
        long requestedRefundAmount = calculateRequestedRefundAmount(refundTargetItems, quantityMap);

        /**
         * 현재 주문에서 아직 환불 가능한 전체 잔여 금액입니다.
         */
        long totalRemainingRefundableAmount = calculateTotalRemainingRefundableAmount(orderItems);

        /**
         * 이번 환불의 최종 환불 금액과 포인트 정산 스냅샷을 계산합니다.
         */
        long currentPointBalance = pointService.getCurrentPointBalanceForUpdate(payment);

        RefundAmount refundAmount = calculateRefundAmount(
                payment,
                requestedRefundAmount,
                totalRemainingRefundableAmount,
                currentPointBalance
        );

        /**
         * 계산된 금액을 기준으로 Refund, RefundItem, RefundOutbox를 생성합니다.
         */
        return createRefundSnapshotAndOutbox(
                idempotencyKey,
                requestHash,
                order,
                payment,
                request.reason(),
                refundTargetItems,
                quantityMap,
                refundAmount
        );
    }

    /**
     * 전체 환불 요청을 처리합니다.
     * 부분 환불과 흐름은 거의 같지만, 환불 대상이 "현재 남아 있는 모든 환불 가능 상품 수량"이라는 점이 다릅니다.
     */
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

        String requestHash = createFullRefundRequestHash(request);

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        Order order = payment.getOrder();

        validateOrderOwner(order, userId);
        validateRefundablePayment(payment);

        Optional<Refund> existingRefund =
                refundRepository.findByPayment_IdAndIdempotencyKey(payment.getId(), idempotencyKey);

        if (existingRefund.isPresent()) {
            validateSameIdempotentRequest(existingRefund.get(), requestHash);

            List<RefundItem> existingItems =
                    refundItemRepository.findAllByRefundIdWithOrderItem(existingRefund.get().getId());
            return RefundResponse.from(existingRefund.get(), existingItems);
        }

        validateNoActiveRefund(payment);

        List<OrderItem> orderItems = orderItemRepository.findAllWithProductByOrderId(order.getId());


        /**
         * 전체 환불에서는 남아 있는 환불 가능 수량이 1개 이상인 상품을 모두 환불 대상으로 잡습니다.
         */
        List<OrderItem> refundTargetItems = orderItems.stream()
                .filter(orderItem -> orderItem.getRemainingRefundableQuantity() > 0)
                .toList();

        if (refundTargetItems.isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        /**
         * 전체 환불이므로 각 주문 상품의 남은 환불 가능 수량 전체를 환불 수량으로 사용합니다.
         */
        Map<Long, Integer> quantityMap = new HashMap<>();
        for (OrderItem orderItem : refundTargetItems) {
            quantityMap.put(orderItem.getId(), orderItem.getRemainingRefundableQuantity());
        }

        /**
         * 전체 환불에서는 현재 남은 환불 가능 금액 전체가 요청 환불 금액입니다.
         */
        long totalRemainingRefundableAmount = calculateTotalRemainingRefundableAmount(orderItems);

        /**
         * 전체 환불은 항상 마지막 환불이므로
         * requestedRefundAmount와 totalRemainingRefundableAmount에 같은 값을 넘깁니다.
         */
        long currentPointBalance = pointService.getCurrentPointBalanceForUpdate(payment);

        RefundAmount refundAmount = calculateRefundAmount(
                payment,
                totalRemainingRefundableAmount,
                totalRemainingRefundableAmount,
                currentPointBalance
        );

        return createRefundSnapshotAndOutbox(
                idempotencyKey,
                requestHash,
                order,
                payment,
                request.reason(),
                refundTargetItems,
                quantityMap,
                refundAmount
        );
    }

    /**
     * 환불 요청 스냅샷과 Outbox 작업을 함께 생성합니다.
     * 1. Refund 엔티티 저장
     * 2. RefundItem 엔티티 생성
     * 3. OrderItem 환불 수량 예약
     * 4. RefundItem 저장
     * 5. RefundOutbox 저장
     * Refund에는 PG 호출 전에 확정되어야 하는 정산 스냅샷을 저장합니다.
     * 그래야 이후 비동기 RefundProcessor가 동일한 기준으로 PG 환불을 처리할 수 있습니다.
     */
    private RefundResponse createRefundSnapshotAndOutbox(
            String idempotencyKey,
            String requestHash,
            Order order,
            Payment payment,
            String reason,
            List<OrderItem> refundTargetItems,
            Map<Long, Integer> quantityMap,
            RefundAmount refundAmount
    ) {
        Refund refund = refundRepository.save(Refund.createRefund(
                idempotencyKey,
                requestHash,
                order,
                payment,
                reason,
                refundAmount.refundAmount(),
                refundAmount.pointRefundAmount(),
                refundAmount.pgRefundAmount(),
                refundAmount.grossPointRefundAmount(),
                refundAmount.grossPgRefundAmount(),
                refundAmount.earnedPointRecoveryAmount(),
                refundAmount.recoveredFromUsedPoint(),
                refundAmount.recoveredFromBalance(),
                refundAmount.deductedFromPgRefund()
        ));

        pointService.reserveEarnedPointRecoveryFromBalance(
                payment,
                refund,
                refundAmount.recoveredFromBalance()
        );

        /**
         * 환불 대상 상품별 스냅샷을 생성합니다.
         * 각 RefundItem에는 해당 상품에서 환불될 수량과 포인트/PG 환불 금액이 저장됩니다.
         */
        List<RefundItem> refundItems = createRefundItems(refund, refundTargetItems, quantityMap, refundAmount);

        /**
         * 환불 요청이 생성되는 순간 수량을 예약합니다.
         * ex)
         * 주문 상품 수량 3개
         * 첫 번째 환불 요청에서 2개 환불 예약
         * 두 번째 요청이 동시에 들어와도 남은 환불 가능 수량은 1개로 계산되어야 합니다.
         * 이 예약이 없으면 PG 환불 완료 전까지 같은 상품 수량을 중복 환불 요청할 수 있습니다.
         */
        for (RefundItem refundItem : refundItems) {
            refundItem.getOrderItem().reserveRefundQuantity(refundItem.getRefundQuantity());
        }

        refundItemRepository.saveAll(refundItems);


        /**
         * 실제 PG 취소는 지금 호출하지 않습니다.
         * 대신 Outbox에 작업을 저장해두고,
         * RefundScheduler 또는 RefundProcessor가 나중에 이 작업을 가져가서 PG 환불을 처리합니다.
         */
        refundOutboxRepository.save(RefundOutbox.create(refund, LocalDateTime.now()));

        return RefundResponse.from(refund, refundItems);
    }


    /**
     * 환불 대상 상품별 RefundItem 스냅샷을 생성합니다.
     * pointRefundAmount와 pgRefundAmount는 전체 환불 금액에서 상품 금액 비율대로 나눕니다.
     * 마지막 상품에는 나눗셈 버림 오차를 몰아서 전체 합계가 정확히 맞도록 합니다.
     */
    private List<RefundItem> createRefundItems(
            Refund refund,
            List<OrderItem> refundTargetItems,
            Map<Long, Integer> quantityMap,
            RefundAmount refundAmount
    ) {
        List<Long> itemGrossAmounts = new ArrayList<>();

        for (OrderItem orderItem : refundTargetItems) {
            int quantity = quantityMap.get(orderItem.getId());
            long itemGrossAmount = (long) orderItem.getPrice() * quantity;

            itemGrossAmounts.add(itemGrossAmount);
        }

        /**
         * 1. 적립 포인트 회수 후 실제 환불 총액을 상품별 gross 금액 비율대로 나눕니다.
         * 적립 포인트 회수 때문에 실제 환불액은 gross 합계보다 작을 수 있습니다.
         */
        List<Long> itemActualRefundAmounts = allocateAmountByCap(
                itemGrossAmounts,
                refundAmount.refundAmount()
        );

        /**
         * 2. 실제 환불액 안에서 포인트 환불액을 다시 나눕니다.
         * 각 상품의 point + pg가 itemActualRefundAmount를 넘지 않게 만들기 위해서입니다.
         */
        List<Long> itemPointRefundAmounts = allocateAmountByCap(
                itemActualRefundAmounts,
                refundAmount.pointRefundAmount()
        );

        List<RefundItem> refundItems = new ArrayList<>();

        for (int i = 0; i < refundTargetItems.size(); i++) {
            OrderItem orderItem = refundTargetItems.get(i);
            int quantity = quantityMap.get(orderItem.getId());

            long itemActualRefundAmount = itemActualRefundAmounts.get(i);
            long itemPointRefundAmount = itemPointRefundAmounts.get(i);
            long itemPgRefundAmount = itemActualRefundAmount - itemPointRefundAmount;

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
    /**
     *  mountToAllocate를 각 항목의 상한 금액 안에서 비율대로 배분합니다.
     * 예를 들어
     * 상품 A 상한 900원,
     * 상품 B 상한 100원이고
     * 실제 배분할 금액이 800원이면,
     * 이 경우 800원을 A/B 비율대로 나누되
     * 어떤 상품도 자기 상한을 넘지 않도록 보장합니다.
     */
    private List<Long> allocateAmountByCap(List<Long> caps, long amountToAllocate) {
        if (caps.isEmpty() || amountToAllocate < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        long totalCap = caps.stream()
                .mapToLong(Long::longValue)
                .sum();

        if (totalCap <= 0 || amountToAllocate > totalCap) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        List<Long> allocations = new ArrayList<>();
        long remainingAmount = amountToAllocate;

        for (Long cap : caps) {
            long allocatedAmount = cap * amountToAllocate / totalCap;

            allocations.add(allocatedAmount);
            remainingAmount -= allocatedAmount;
        }

        /**
         * 정수 나눗셈에서 버려진 나머지 금액을 1원씩 분산합니다.
         * 단, 각 항목의 상한 금액은 절대 넘지 않습니다.
         */
        int index = 0;
        while (remainingAmount > 0) {
            long currentAmount = allocations.get(index);
            long cap = caps.get(index);

            if (currentAmount < cap) {
                allocations.set(index, currentAmount + 1);
                remainingAmount--;
            }

            index = (index + 1) % allocations.size();
        }

        return allocations;
    }

    /**
     * 부분 환불 요청으로 들어온 상품 목록이 실제 환불 가능한지 검증하고,
     * 환불 대상 OrderItem 목록을 반환합니다.
     * 검증 내용:
     * 1. 같은 orderItemId가 중복 요청되었는지
     * 2. 요청한 orderItemId가 실제 주문에 포함되어 있는지
     * 3. 환불 수량이 null이거나 0 이하인지
     * 4. 요청 수량이 남은 환불 가능 수량을 초과하는지
     */
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

    /**
     * 부분 환불 요청의 item 목록을 orderItemId -> quantity 형태로 변환합니다.
     */
    private Map<Long, Integer> toQuantityMap(List<RefundItemRequest> itemRequests) {
        Map<Long, Integer> quantityMap = new HashMap<>();

        for (RefundItemRequest itemRequest : itemRequests) {
            quantityMap.put(itemRequest.orderItemId(), itemRequest.quantity());
        }

        return quantityMap;
    }

    /**
     * 이번 환불 요청의 상품 기준 총 환불 금액을 계산합니다.
     */
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

    /**
     * 현재 주문에서 아직 환불 가능한 전체 잔여 금액입니다.
     * requestedRefundAmount == totalRemainingRefundableAmount 이면 이번 환불은 마지막 환불로 판단합니다.
     * 마지막 환불에서는 부분 환불 비율 계산 중 생긴 버림 오차를 없애기 위해 남은 금액 전체를 기준으로 계산합니다.
     */
    private long calculateTotalRemainingRefundableAmount(List<OrderItem> orderItems) {
        long totalRemainingAmount = 0L;

        for (OrderItem orderItem : orderItems) {
            totalRemainingAmount += (long) orderItem.getPrice() * orderItem.getRemainingRefundableQuantity();
        }

        if (totalRemainingAmount <= 0) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        return totalRemainingAmount;
    }

    /**
     * 이번 환불 요청에 대한 환불 금액과 포인트 정산 금액을 계산합니다.
     * 계산 흐름:
     * 1. 이번 요청이 마지막 환불인지 판단합니다.
     * 2. 적립 포인트 회수 전 원래 환불 예정 금액을 계산합니다.
     * 3. 이번 환불에서 회수해야 하는 적립 포인트 금액을 계산합니다.
     * 4. RefundPointSettlementCalculator에게 실제 포인트 정산 계산을 위임합니다.
     * 5. 계산 결과를 검증한 뒤 RefundAmount로 반환합니다.
     */
    private RefundAmount calculateRefundAmount(
            Payment payment,
            long requestedRefundAmount,
            long totalRemainingRefundableAmount,
            long currentPointBalance
    ) {
        boolean finalRefund = requestedRefundAmount == totalRemainingRefundableAmount;

        long grossPointRefundAmount;
        long grossPgRefundAmount;
        long earnedPointRecoveryAmount;

        if (finalRefund) {
            /*
             * 마지막 환불에서는 비율 계산으로 생긴 버림 오차를 제거하기 위해
             * 결제 당시 전체 금액에서 이미 완료된 gross 환불 금액을 뺀 값을 사용합니다.
             *
             * 여기서 최종 환불액(pointRefundAmount, pgRefundAmount) 합계를 사용하면 안 됩니다.
             * 이전 부분 환불에서 적립 포인트 회수 때문에 최종 환불액이 줄어들 수 있기 때문입니다.
             */
            long completedGrossPointRefundAmount =
                    refundRepository.sumCompletedGrossPointRefundAmount(payment.getId());

            long completedGrossPgRefundAmount =
                    refundRepository.sumCompletedGrossPgRefundAmount(payment.getId());

            long completedEarnedPointRecoveryAmount =
                    refundRepository.sumCompletedEarnedPointRecoveryAmount(payment.getId());

            grossPointRefundAmount = payment.getUsedPointAmount() - completedGrossPointRefundAmount;
            grossPgRefundAmount = payment.getPgAmount() - completedGrossPgRefundAmount;

            /*
             * 마지막 환불에서는 남은 적립 포인트 회수 대상 전체를 잡습니다.
             * 이전 부분 환불에서 이미 회수한 적립 포인트는 제외합니다.
             */
            earnedPointRecoveryAmount =
                    payment.getRewardPointAmount() - completedEarnedPointRecoveryAmount;
        } else {
            /*
             * 마지막 환불이 아닌 경우에는 요청 금액 비율에 따라
             * 사용 포인트 환불 예정액과 PG 환불 예정액을 계산합니다.
             */
            grossPointRefundAmount = calculatePointRefundAmount(payment, requestedRefundAmount);
            grossPgRefundAmount = requestedRefundAmount - grossPointRefundAmount;

            /*
             * 부분 환불에서는 실제 PG 환불 예정 금액에 비례해서
             * 이번 환불에서 회수할 적립 포인트 금액을 계산합니다.
             */
            earnedPointRecoveryAmount = calculateEarnedPointRecoveryAmount(payment, grossPgRefundAmount);
        }

        /*
         * 실제 포인트 정산 계산을 전용 Calculator에게 위임합니다.
         */
        RefundPointSettlementCalculator.RefundPointSettlement settlement =
                refundPointSettlementCalculator.calculate(
                        grossPointRefundAmount,
                        grossPgRefundAmount,
                        earnedPointRecoveryAmount,
                        currentPointBalance
                );

        validateCalculatedAmount(
                requestedRefundAmount,
                settlement.grossPointRefundAmount(),
                settlement.grossPgRefundAmount(),
                settlement.earnedPointRecoveryAmount(),
                settlement.pointRefundAmount(),
                settlement.pgRefundAmount(),
                settlement.recoveredFromUsedPoint(),
                settlement.recoveredFromBalance(),
                settlement.deductedFromPgRefund()
        );

        /*
         * Refund 엔티티의 refundAmount는 고객에게 실제로 환불되는 최종 금액입니다.
         * 즉, 최종 반환 포인트 + 최종 PG 환불 금액입니다.
         */
        long actualRefundAmount = settlement.pointRefundAmount() + settlement.pgRefundAmount();

        if (actualRefundAmount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        return new RefundAmount(
                requestedRefundAmount,
                actualRefundAmount,
                settlement.grossPointRefundAmount(),
                settlement.grossPgRefundAmount(),
                settlement.earnedPointRecoveryAmount(),
                settlement.pointRefundAmount(),
                settlement.pgRefundAmount(),
                settlement.recoveredFromUsedPoint(),
                settlement.recoveredFromBalance(),
                settlement.deductedFromPgRefund()
        );
    }

    /**
     * 진행 중 환불 검증
     * 같은 결제에 대해 PROCESSING 또는 PG_RESULT_UNKNOWN 상태의 환불이 있으면 새 환불 요청을 막습니다.
     */
    private void validateNoActiveRefund(Payment payment) {
        boolean exists = refundRepository.existsByPayment_IdAndStatusIn(
                payment.getId(),
                List.of(RefundStatus.PROCESSING, RefundStatus.PG_RESULT_UNKNOWN)
        );

        if (exists) {
            throw new BusinessException(ErrorCode.REFUND_IN_PROGRESS);
        }
    }

    /**
     * Payment 상태 검증: 결제가 환불 가능한 상태인지 확인합니다.
     * 현재 정책:
     * COMPLETED: 결제가 완료된 상태이므로 환불 가능
     * PARTIAL_REFUNDED: 이미 일부 환불되었지만 남은 금액이 있으면 추가 환불 가능
     */
    private void validateRefundablePayment(Payment payment) {
        switch (payment.getStatus()) {
            case COMPLETED, PARTIAL_REFUNDED -> {
            }
            default -> throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }
    }

    /**
     * 소유권 검증: 요청한 userId가 해당 주문의 소유자인지 확인합니다.
     */
    private void validateOrderOwner(Order order, Long userId) {
        if (!Objects.equals(order.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    /**
     * 멱등키가 비어 있는지 검증합니다.
     * 멱등키는 같은 환불 요청이 중복 생성되지 않도록 막기 위한 키입니다.
     */
    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    /**
     * 부분 환불 요청값을 검증합니다.
     * 부분 환불에서는 환불 사유와 환불 대상 상품 목록이 필수입니다.
     */
    private void validatePartialRequest(PartialRefundRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_ITEM_REQUIRED);
        }
    }

    /**
     * 환불 금액 계산 결과를 담는 내부 값 객체입니다.
     *
     * @param requestedRefundAmount 상품 기준 이번 환불 요청 금액
     * @param refundAmount 고객에게 실제로 환불되는 최종 금액 (pointRefundAmount + pgRefundAmount)
     * @param grossPointRefundAmount 적립 포인트 회수 전, 원래 반환 예정이었던 사용 포인트 금액
     * @param grossPgRefundAmount 적립 포인트 회수 전, 원래 PG로 환불하려던 금액
     * @param earnedPointRecoveryAmount 이번 환불에서 회수해야 하는 적립 포인트 금액
     * @param pointRefundAmount 고객에게 실제 반환할 사용 포인트 금액
     * @param pgRefundAmount PortOne에 실제 취소 요청할 PG 환불 금액
     * @param recoveredFromUsedPoint 반환 예정 사용 포인트에서 상계한 적립 포인트 금액
     * @param recoveredFromBalance 고객의 현재 보유 포인트 잔액에서 차감할 적립 포인트 금액
     * @param deductedFromPgRefund 보유 포인트로도 회수하지 못해 PG 환불 금액에서 차감한 금액
     */
    private record RefundAmount(
            long requestedRefundAmount,
            long refundAmount,
            long grossPointRefundAmount,
            long grossPgRefundAmount,
            long earnedPointRecoveryAmount,
            long pointRefundAmount,
            long pgRefundAmount,
            long recoveredFromUsedPoint,
            long recoveredFromBalance,
            long deductedFromPgRefund
    ) {
    }

    /**
     * 요청 환불 금액 중 사용 포인트로 돌려줘야 할 원래 금액을 계산합니다.
     *
     * 예를 들어 전체 결제 금액 중 20%를 사용 포인트로 결제했다면,
     * 부분 환불 금액에서도 같은 비율만큼 사용 포인트 환불 예정액을 계산합니다.
     */
    private long calculatePointRefundAmount(Payment payment, long refundAmount) {
        if (payment.getTotalAmount() == 0L || payment.getUsedPointAmount() == 0L) {
            return 0L;
        }

        return refundAmount * payment.getUsedPointAmount() / payment.getTotalAmount();
    }

    /**
     * 이번 부분 환불에서 회수해야 할 적립 포인트 금액을 계산합니다.
     *
     * 적립 포인트는 PG 결제 금액을 기준으로 지급되었다고 보고,
     * 이번 환불의 grossPgRefundAmount가 원래 PG 결제 금액에서 차지하는 비율만큼 회수합니다.
     *
     * 마지막 환불에서는 이 메서드를 사용하지 않고,
     * 남은 적립 포인트 회수 대상 전체를 별도로 계산합니다.
     */
    private long calculateEarnedPointRecoveryAmount(Payment payment, long grossPgRefundAmount) {
        if (payment.getPgAmount() == 0L || payment.getRewardPointAmount() == 0L) {
            return 0L;
        }

        return grossPgRefundAmount * payment.getRewardPointAmount() / payment.getPgAmount();
    }

    /**
     * 환불 정산 계산 결과가 정책적으로 일관적인지 검증합니다.
     *
     * 검증 관계:
     * 1. 요청 환불 금액 = 원래 포인트 환불 예정액 + 원래 PG 환불 예정액
     * 2. 원래 포인트 환불 예정액 = 최종 포인트 환불액 + 사용 포인트에서 회수한 금액
     * 3. 원래 PG 환불 예정액 = 최종 PG 환불액 + PG 환불액에서 차감한 금액
     * 4. 회수해야 할 적립 포인트 = 사용 포인트 회수 + 보유 포인트 회수 + PG 환불 차감
     *
     * 주의:
     * recoveredFromBalance는 고객의 기존 보유 포인트에서 차감하는 금액입니다.
     * 따라서 requestedRefundAmount 계산에 직접 더하면 안 됩니다.
     */
    private void validateCalculatedAmount(
            long requestedRefundAmount,
            long grossPointRefundAmount,
            long grossPgRefundAmount,
            long earnedPointRecoveryAmount,
            long pointRefundAmount,
            long pgRefundAmount,
            long recoveredFromUsedPoint,
            long recoveredFromBalance,
            long deductedFromPgRefund
    ) {
        if (requestedRefundAmount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (grossPointRefundAmount < 0
                || grossPgRefundAmount < 0
                || earnedPointRecoveryAmount < 0
                || pointRefundAmount < 0
                || pgRefundAmount < 0
                || recoveredFromUsedPoint < 0
                || recoveredFromBalance < 0
                || deductedFromPgRefund < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (requestedRefundAmount != grossPointRefundAmount + grossPgRefundAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (grossPointRefundAmount != pointRefundAmount + recoveredFromUsedPoint) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (grossPgRefundAmount != pgRefundAmount + deductedFromPgRefund) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (earnedPointRecoveryAmount
                != recoveredFromUsedPoint + recoveredFromBalance + deductedFromPgRefund) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private void validateSameIdempotentRequest(Refund existingRefund, String requestHash) {
        if (!existingRefund.getRequestHash().equals(requestHash)) {
            /*
             * 같은 Idempotency-Key인데 요청 내용이 다르면 재시도가 아니라 충돌입니다.
             * 기존 환불을 그대로 반환하면 사용자는 다른 요청이 성공한 것처럼 오해할 수 있습니다.
             */
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private String createPartialRefundRequestHash(PartialRefundRequest request) {
        String itemKey = request.items().stream()
                .sorted(Comparator.comparing(RefundItemRequest::orderItemId))
                .map(item -> item.orderItemId() + ":" + item.quantity())
                .collect(Collectors.joining("|"));

        return sha256(request.reason() + "|" + itemKey);
    }

    private String createFullRefundRequestHash(FullRefundRequest request) {
        return sha256(request.reason());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}