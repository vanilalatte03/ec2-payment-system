package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.product.entity.Product;
import com.teamec2.paymentsystem.domain.product.repository.ProductRepository;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;
import com.teamec2.paymentsystem.domain.refund.entity.RefundOutbox;
import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PG 환불 처리 전후의 DB 트랜잭션 상태 변경 담당
 * 이 서비스의 책임:
 * 1. PG 호출 전 Outbox 선점
 * 2. PG 호출 성공 후 내부 DB 상태 반영
 * 3. PG 호출 실패 확정 시 예약 수량/포인트 해제
 * 4. PG 결과 미확정 시 재시도 처리
 * 5. PortOne cancellationId 기준 웹훅 완료 처리
 */
@Service
@RequiredArgsConstructor
public class RefundProcessingTxService {

    private final RefundOutboxRepository refundOutboxRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final PointService pointService;
    private final ProductRepository productRepository;

    public record RefundWebhookProcessResult(
            Payment payment,
            Refund refund
    ) {
    }

    /**
     * PG 환불 API 호출 전에 Outbox를 PROCESSING 상태로 선점하고,
     * PG 호출에 필요한 데이터만 RefundCancelCommand로 만들어 반환합니다.
     */
    @Transactional
    public Optional<RefundCancelCommand> start(Long outboxId) {
        RefundOutbox outbox = findOutboxForUpdate(outboxId);

        if (outbox.getStatus() != RefundOutboxStatus.PENDING) {
            return Optional.empty();
        }

        Refund refund = outbox.getRefund();
        Payment payment = refund.getPayment();

        /*
         * 환불 상태 검증
         * PG API 호출 중 타임아웃이 발새할 수 있기 때문에 PG_RESULT_UNKNOWN도 허용합니다.
         */
        if (!refund.isProcessing() && !refund.isPgResultUnknown()) {
            outbox.markFailed("처리 가능한 환불 상태가 아닙니다.");
            return Optional.empty();
        }

        /*
         * 이미 완료된 PG 환불 금액 합계 조회
         * 초과 환불을 막기 위해 이미 완료된 PG 환불 금액 합계를 조회합니다.
         */
        Long completedPgRefundAmount = refundRepository.sumCompletedPgRefundAmount(payment.getId());

        /*
         * 현재 PG 환불 가능 금액 계산
         * 이번 환불 요청이 남은 결제 금액을 초과하지 않는지 검증
         * 현재 PG에서 취소 가능한 남은 금액을 계산합니다.
         * 현재 취소 가능 금액 = 원래 PG 결재 금액 - 이미 완료된 PG 환불 금액
         */
        Long currentCancellableAmount = payment.getPgAmount() - completedPgRefundAmount;

        // 현재 DB 기준으로 PG 환불 가능 금액을 초과하면 외부 PG 호출 없이 실패 처리합니다.
        if (refund.getPgRefundAmount() > currentCancellableAmount) {
            fail(outboxId, "PG 환불 가능 금액보다 요청 금액이 큽니다.");
            return Optional.empty();
        }

        /*
         * Outbox 상태 변경
         * PENDING -> PROCESSING
         */
        outbox.markProcessing(LocalDateTime.now());

        return Optional.of(new RefundCancelCommand(
                refund.getId(),
                refund.getPortonePaymentId(),
                refund.getPgRefundAmount(),
                currentCancellableAmount,
                refund.getReason()
        ));
    }

    /**
     * PG 취소 ID가 없는 환불 완료 처리입니다.
     */
    @Transactional
    public void complete(Long outboxId) {
        RefundOutbox outbox = findOutboxForUpdate(outboxId);
        completeOutbox(outbox);
    }

    /**
     * PG 환불이 성공한 뒤 PortOne 취소 ID를 기록하고 DB 상태를 최종 반영합니다.
     */
    @Transactional
    public void complete(Long outboxId, String portoneCancellationId) {
        RefundOutbox outbox = findOutboxForUpdate(outboxId);
        outbox.getRefund().recordPortoneCancellationId(portoneCancellationId);
        completeOutbox(outbox);
    }

    /**
     * PortOne 취소 완료 웹훅을 기준으로 내부 환불 완료 상태를 확정합니다.
     */
    @Transactional
    public RefundWebhookProcessResult completeByPortoneCancellationId(
            String portonePaymentId,
            String portoneCancellationId
    ) {
        if (portonePaymentId == null || portonePaymentId.isBlank()) {
            throw new BusinessException(ErrorCode.WEBHOOK_PAYMENT_ID_MISSING);
        }

        if (portoneCancellationId == null || portoneCancellationId.isBlank()) {
            throw new BusinessException(ErrorCode.WEBHOOK_CANCELLATION_ID_MISSING);
        }

        Optional<RefundOutbox> processableOutbox =
                refundOutboxRepository.findProcessableByPortoneCancellationIdForUpdate(
                        portonePaymentId,
                        portoneCancellationId
                );

        if (processableOutbox.isPresent()) {
            RefundOutbox outbox = processableOutbox.get();

            if (outbox.getStatus() == RefundOutboxStatus.PENDING) {
                outbox.markProcessing(LocalDateTime.now());
            }

            Refund refund = outbox.getRefund();
            completeOutbox(outbox);
            return new RefundWebhookProcessResult(refund.getPayment(), refund);
        }

        Optional<RefundOutbox> recoverableFailedOutbox =
                refundOutboxRepository.findRecoverableFailedByPortoneCancellationIdForUpdate(
                        portonePaymentId,
                        portoneCancellationId
                );

        if (recoverableFailedOutbox.isPresent()) {
            RefundOutbox outbox = recoverableFailedOutbox.get();
            outbox.markProcessingForWebhookRecovery(LocalDateTime.now());

            Refund refund = outbox.getRefund();
            completeOutbox(outbox);
            return new RefundWebhookProcessResult(refund.getPayment(), refund);
        }

        Optional<Refund> alreadyCompletedRefund = refundRepository
                .findByPortonePaymentIdAndPortoneCancellationId(portonePaymentId, portoneCancellationId);

        if (alreadyCompletedRefund.isPresent() && alreadyCompletedRefund.get().isCompleted()) {
            Refund refund = alreadyCompletedRefund.get();
            return new RefundWebhookProcessResult(refund.getPayment(), refund);
        }

        Optional<RefundWebhookProcessResult> unidentifiedWebhookResult =
                completeUnidentifiedWebhookCandidate(portonePaymentId, portoneCancellationId);

        if (unidentifiedWebhookResult.isPresent()) {
            return unidentifiedWebhookResult.get();
        }

        throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
    }

    private Optional<RefundWebhookProcessResult> completeUnidentifiedWebhookCandidate(
            String portonePaymentId,
            String portoneCancellationId
    ) {
        List<RefundOutbox> candidates =
                refundOutboxRepository.findUnidentifiedWebhookCandidatesForUpdate(portonePaymentId);

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        if (candidates.size() != 1) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        RefundOutbox outbox = candidates.get(0);
        Refund refund = outbox.getRefund();

        if (outbox.getStatus() == RefundOutboxStatus.FAILED && !refund.isPgResultUnknown()) {
            throw new BusinessException(ErrorCode.REFUND_NOT_ALLOWED);
        }

        refund.recordPortoneCancellationId(portoneCancellationId);

        if (outbox.getStatus() == RefundOutboxStatus.PENDING) {
            outbox.markProcessing(LocalDateTime.now());
        }

        if (outbox.getStatus() == RefundOutboxStatus.FAILED) {
            outbox.markProcessingForWebhookRecovery(LocalDateTime.now());
        }

        completeOutbox(outbox);
        return Optional.of(new RefundWebhookProcessResult(refund.getPayment(), refund));
    }

    private void completeOutbox(RefundOutbox outbox) {
        Refund refund = outbox.getRefund();
        Payment payment = refund.getPayment();
        Order order = refund.getOrder();

        if (refund.isCompleted()) {
            outbox.markSucceeded();
            return;
        }

        List<RefundItem> refundItems =
                refundItemRepository.findAllByRefundIdWithOrderItem(refund.getId());

        Map<Long, Product> lockedProducts = lockProductsForRefund(refundItems);

        for (RefundItem refundItem : refundItems) {
            OrderItem orderItem = refundItem.getOrderItem();
            Product lockedProduct = lockedProducts.get(orderItem.getProductId());

            orderItem.refund(refundItem.getRefundQuantity(), lockedProduct);
        }

        pointService.restoreUsedPoints(
                payment,
                refund,
                refund.getPointRefundAmount()
        );

        /*
         * 현재 주문의 모든 주문 상품을 조회하여
         * 환불이 끝난 뒤 이 주문이 전체 환불 상태인지 부분 환불 상태인지 판단합니다.
         */
        List<OrderItem> allOrderItems = orderItemRepository.findWithProductByOrderId(order.getId());

        /*
         * 전체 환불 여부 판단
         * 모든 주문 상품의 남은 환불 가능 수량이 0이면 전체 환불로 판단합니다.
         */
        boolean fullyRefunded = allOrderItems.stream()
                .allMatch(orderItem -> orderItem.getRemainingRefundableQuantity() == 0);

        if (fullyRefunded) {
            payment.markAsRefunded();
            order.cancelByRefund();
        } else {
            payment.markAsPartialRefunded();
        }

        refund.complete(LocalDateTime.now());
        outbox.markSucceeded();
    }

    /**
     * 환불 처리가 실패로 확정된 경우 DB 상태를 정리합니다.
     */
    @Transactional
    public void fail(Long outboxId, String reason) {
        RefundOutbox outbox = findOutboxForUpdate(outboxId);
        Refund refund = outbox.getRefund();

        /*
         * 이미 실패 처리된 환불이면 수량 해제를 다시 하지 않습니다.
         */
        if (refund.isFailed()) {
            outbox.markFailed(reason);
            return;
        }

        List<RefundItem> refundItems = refundItemRepository.findAllByRefundIdWithOrderItem(refund.getId());

        for (RefundItem refundItem : refundItems) {
            refundItem.getOrderItem().releaseRefundQuantity(refundItem.getRefundQuantity());
        }

        pointService.releaseReservedEarnedPointRecovery(
                refund.getPayment(),
                refund
        );

        refund.fail(reason);
        outbox.markFailed(reason);
    }

    /**
     * PG 환불 결과가 성공인지 실패인지 확정되지 않은 경우 호출합니다.
     */
    @Transactional
    public void retryAsPgResultUnknown(Long outboxId, String portoneCancellationId, String reason) {
        RefundOutbox outbox = findOutboxForUpdate(outboxId);
        outbox.getRefund().recordPortoneCancellationId(portoneCancellationId);
        retryAsPgResultUnknown(outbox, reason);
    }

    private void retryAsPgResultUnknown(RefundOutbox outbox, String reason) {
        Refund refund = outbox.getRefund();

        String message = reason == null || reason.isBlank()
                ? "PG 취소 결과를 확정하지 못했습니다."
                : reason;

        if (refund.isProcessing()) {
            refund.markPgResultUnknown(message);
        }

        boolean retryScheduled = outbox.markRetry(message, LocalDateTime.now());

        if (!retryScheduled) {
            /*
             * PG_RESULT_UNKNOWN은 실제 PG에서 이미 환불 성공했을 가능성이 있습니다.
             * 따라서 자동으로 Refund FAILED 처리하거나 예약 수량/포인트를 해제하면
             * 같은 상품을 다시 환불할 수 있어 중복 환불 위험이 생깁니다.
             * 재시도 초과 시에는 Outbox만 FAILED로 멈추고,
             * Refund는 PG_RESULT_UNKNOWN으로 남겨 운영자가 PortOne 관리자 콘솔/API로 확인해야 합니다.
             */
            return;
        }
    }

    /**
     * cancellationId가 없는 PG_RESULT_UNKNOWN 처리입니다.
     */
    @Transactional
    public void retryAsPgResultUnknown(Long outboxId, String reason) {
        RefundOutbox outbox = findOutboxForUpdate(outboxId);
        retryAsPgResultUnknown(outbox, reason);
    }

    private RefundOutbox findOutboxForUpdate(Long outboxId) {
        return refundOutboxRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Map<Long, Product> lockProductsForRefund(List<RefundItem> refundItems) {
        List<Long> productIds = refundItems.stream()
                .map(refundItem -> refundItem.getOrderItem().getProductId())
                .distinct()
                .sorted()
                .toList();

        if (productIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Product> lockedProducts = productRepository.findAllByIdsWithLock(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        if (lockedProducts.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        return lockedProducts;
    }
}
