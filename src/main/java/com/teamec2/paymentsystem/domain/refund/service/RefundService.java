package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.refund.dto.FullRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.PartialRefundRequest;
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
    private final PointService pointService;
    private final RefundAmountCalculator refundAmountCalculator;
    private final RefundIdempotencyService refundIdempotencyService;
    private final RefundTargetResolver refundTargetResolver;


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
        refundIdempotencyService.validateIdempotencyKey(idempotencyKey);
        validatePartialRequest(request);

        String requestHash = refundIdempotencyService.createPartialRefundRequestHash(request);

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
            refundIdempotencyService.validateSameIdempotentRequest(existingRefund.get(), requestHash);

            List<RefundItem> existingItems =
                    refundItemRepository.findAllByRefundIdWithOrderItem(existingRefund.get().getId());
            return RefundResponse.from(existingRefund.get(), existingItems);
        }

        validateNoActiveRefund(payment);

        /**
         * 주문 상품을 상품 정보와 함께 조회하여 추후 환불 대상 상품 검증, 환불 가능 수량 금증, 환불 금액 계산에 사용합니다.
         */
        List<OrderItem> orderItems = orderItemRepository.findAllWithProductByOrderId(order.getId());

        RefundTarget refundTarget = refundTargetResolver.resolvePartial(orderItems, request.items());

        /**
         * 이번 환불의 최종 환불 금액과 포인트 정산 스냅샷을 계산합니다.
         */
        long currentPointBalance = pointService.getCurrentPointBalanceForUpdate(payment);

        RefundAmount refundAmount = refundAmountCalculator.calculate(
                payment,
                refundTarget.requestedRefundAmount(),
                refundTarget.totalRemainingRefundableAmount(),
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
                refundTarget.refundTargetItems(),
                refundTarget.quantityMap(),
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
        refundIdempotencyService.validateIdempotencyKey(idempotencyKey);

        if (request == null
                || request.reason() == null
                || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        String requestHash = refundIdempotencyService.createFullRefundRequestHash(request);

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        Order order = payment.getOrder();

        validateOrderOwner(order, userId);
        validateRefundablePayment(payment);

        Optional<Refund> existingRefund =
                refundRepository.findByPayment_IdAndIdempotencyKey(payment.getId(), idempotencyKey);

        if (existingRefund.isPresent()) {
            refundIdempotencyService.validateSameIdempotentRequest(existingRefund.get(), requestHash);

            List<RefundItem> existingItems =
                    refundItemRepository.findAllByRefundIdWithOrderItem(existingRefund.get().getId());
            return RefundResponse.from(existingRefund.get(), existingItems);
        }

        validateNoActiveRefund(payment);

        List<OrderItem> orderItems = orderItemRepository.findAllWithProductByOrderId(order.getId());

        RefundTarget refundTarget = refundTargetResolver.resolveFull(orderItems);

        /**
         * 전체 환불은 항상 마지막 환불이므로
         * requestedRefundAmount와 totalRemainingRefundableAmount에 같은 값을 넘깁니다.
         */
        long currentPointBalance = pointService.getCurrentPointBalanceForUpdate(payment);

        RefundAmount refundAmount = refundAmountCalculator.calculate(
                payment,
                refundTarget.requestedRefundAmount(),
                refundTarget.totalRemainingRefundableAmount(),
                currentPointBalance
        );

        return createRefundSnapshotAndOutbox(
                idempotencyKey,
                requestHash,
                order,
                payment,
                request.reason(),
                refundTarget.refundTargetItems(),
                refundTarget.quantityMap(),
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
}