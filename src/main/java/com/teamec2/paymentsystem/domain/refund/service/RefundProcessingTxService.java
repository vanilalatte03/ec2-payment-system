package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderItem;
import com.teamec2.paymentsystem.domain.order.repository.OrderItemRepository;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.domain.refund.entity.Refund;
import com.teamec2.paymentsystem.domain.refund.entity.RefundItem;
import com.teamec2.paymentsystem.domain.refund.entity.RefundOutbox;
import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RefundProcessingTxService {

    private final RefundOutboxRepository refundOutboxRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final PointService pointService;

    /**
     * PG 환불 API 호출 전에 Outbox를 PROCESSING 상태로 선점하고,
     * PG 호출에 필요한 데이터만 RefundCancelCommand로 만들어 반환합니다.
     */
    @Transactional
    public Optional<RefundCancelCommand> start(Long outboxId) {

        /**
         * outboxId에 해당하는 Outbox를 DB에서 조회합니다.
         */
        RefundOutbox outbox = findOutboxForUpdate(outboxId);

        /**
         * Outbox의 상태를 확인합니다.
         */
        if (outbox.getStatus() != RefundOutboxStatus.PENDING) {
            return Optional.empty();
        }

        /**
         * Outbox에서 연결된 Refund를 가져오고
         * 그 Refund에 연결된 Payment를 가져옵니다.
         * 실제 환불금액, 사유, 결제 정보는 Refund와 Payment에 있기 때문입니다.
         */
        Refund refund = outbox.getRefund();
        Payment payment = refund.getPayment();

        /**
         * 환불 상태 검증
         * PG API 호출 중 타임아웃이 발새할 수 있기 때문에 PG_RESULT_UNKNOWN도 허용합니다.
         */
        if (!refund.isProcessing() && !refund.isPgResultUnknown()) {
            outbox.markFailed("처리 가능한 환불 상태가 아닙니다.");
            return Optional.empty();
        }

        /**
         * 이미 완료된 PG 환불 금액 합계 조회
         * 초과 환불을 막기 위해 이미 완료된 PG 환불 금액 합계를 조회합니다.
         */
        Long completedPgRefundAmount = refundRepository.sumCompletedPgRefundAmount(payment.getId());

        /**
         * 현재 PG 환불 가능 금액 계산
         * 이번 환불 요청이 남은 결제 금액을 초과하지 않는지 검증
         * 현재 PG에서 취소 가능한 남은 금액을 계산합니다.
         * 현재 취소 가능 금액 = 원래 PG 결재 금액 - 이미 완료된 PG 환불 금액
         */
        Long currentCancellableAmount = payment.getPgAmount() - completedPgRefundAmount;

        /**
         * 현재 DB 기준으로 PG 환불 가능 금액을 초과하면 외부 PG 호출 없이 실패 처리합니다.
         */
        if (refund.getPgRefundAmount() > currentCancellableAmount) {
            fail(outboxId, "PG 환불 가능 금액보다 요청 금액이 큽니다.");
            return Optional.empty();
        }

        /**
         * Outbox 상태 변경
         * PENDING -> PROCESSING
         */
        outbox.markProcessing(LocalDateTime.now());

        /**
         * 외부 PG 환불 API 호출에 필요한 데이터를 모아서 RefundCancelCommand 객체로 반화합니다.
         * PG 호출에 필요한 데이터를 Entity 그대로 넘기지 않고 필요한 값만 모은 Command로 넘깁니다.
         * => 엔티티를 외부 API호출 계층까지 들고 가지 않고 트랜잭션 밖에서도 필요한 데이터로만 안전하게 사용할 수 있습니다.
         * => 만약 엔티티를 그대로 넘기면 트랜잭션 종료 후 Lazy Loading문제가 생길 수 있습니다.
         */
        return Optional.of(new RefundCancelCommand(
                refund.getId(),
                refund.getPortonePaymentId(),
                refund.getPgRefundAmount(),
                currentCancellableAmount,
                refund.getReason()
        ));
    }

    /**
     * PG 환불이 성공한 뒤 DB 상태를 최종 반영합니다.
     */
    @Transactional
    public void complete(Long outboxId) {

        RefundOutbox outbox = findOutboxForUpdate(outboxId);
        Refund refund = outbox.getRefund();
        Payment payment = refund.getPayment();
        Order order = refund.getOrder();

        /**
         * 이미 완료된 환불이면 멱등성을 위해 Outbox만 성공 상태로 맞춥니다.
         */
        if (refund.isCompleted()) {
            outbox.markSucceeded();
            return;
        }

        /**
         * 현재 환불에 포함된 호나불 상품 상세 목록을 조회합니다.
         */
        List<RefundItem> refundItems =
                refundItemRepository.findAllByRefundIdWithOrderItem(refund.getId());

        /**
         * 환불 상품별 환불 완료 수량을 반영합니다.
         */
        for (RefundItem refundItem : refundItems) {
            refundItem.getOrderItem().refund(refundItem.getRefundQuantity());
        }

        /**
         * 사용 포인트 복구입니다.
         */
        pointService.restoreUsedPoints(payment, refund, refund.getPointRefundAmount());

         // 결제 시 적립했던 포인트 반환 일단 추가해야함

        /**
         * 현재 주문의 모든 주문 상품을 조회하여 환불이 끝난 뒤 이 주문이 전체 환불상태인지 부분 환불 상태인지 판단해야합니다.
         * 이를 판단하려면 이번 환불 대상만 보면 안 되고 주문 전체 상품을 봐야합니다.
         */
        List<OrderItem> allOrderItems = orderItemRepository.findWithProductByOrderId(order.getId());

        /**
         * 전체 환불 여부 판단
         * 모든 주문 상품의 남은 환불 가능 수량이 0이면 전체 환불로 판단합니다.
         * 결제 상태를 REFUNDED 로 할지 PARTIAL_REFUNDED 로 할지 결정하기 위함입니다.
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

        /**
         * 이미 실패 처리된 환불이면 수량 해제를 다시 하지 않습니다.
         */
        if (refund.isFailed()) {
            outbox.markFailed(reason);
            return;
        }

        List<RefundItem> refundItems = refundItemRepository.findAllByRefundIdWithOrderItem(refund.getId());

        /**
         * 환불 요청 생성 시 예약했던 수량을 다시 해제합니다.
         */
        for (RefundItem refundItem : refundItems) {
            refundItem.getOrderItem().releaseRefundQuantity(refundItem.getRefundQuantity());
        }

        refund.fail(reason);
        outbox.markFailed(reason);
    }

    /**
     * PG 환불 결과가 성공인지 실패인지 확정되지 않은 경우 호출합니다.
     */
    @Transactional
    public void retryAsPgResultUnknown(Long outboxId, String reason) {
        RefundOutbox outbox = findOutboxForUpdate(outboxId);
        Refund refund = outbox.getRefund();

        String message = reason == null || reason.isBlank()
                ? "PG 취소 결과를 확정하지 못했습니다."
                : reason;

        if (refund.isProcessing()) {
            refund.markPgResultUnknown(message);
        }

        boolean retryScheduled = outbox.markRetry(message, LocalDateTime.now());

        /**
         * 최대 재시도 횟수를 초과한 경우입니다.
         *
         * 이때 Outbox만 FAILED로 끝내면 Refund는 PG_RESULT_UNKNOWN으로 남고,
         * 이후 새 환불 요청이 REFUND_IN_PROGRESS로 계속 막힙니다.
         *
         * 따라서 Refund도 FAILED로 전환하고,
         * 환불 요청 생성 시 예약했던 OrderItem 수량을 해제해야 합니다.
         */
        if (!retryScheduled) {
            failRefundAfterRetryExceeded(
                    refund,
                    "최대 재시도 횟수를 초과하여 환불 실패로 확정했습니다. 마지막 오류: " + message
            );
        }
    }

    /**
     * 재시도 한도를 초과한 환불을 최종 실패로 정리합니다.
     * PG_RESULT_UNKNOWN 상태가 계속 남으면 새 환불 요청이 계속 REFUND_IN_PROGRESS로 막히므로,
     * 최종 실패 시 Refund 상태를 FAILED로 바꾸고 예약 수량을 해제합니다.
     */
    private void failRefundAfterRetryExceeded(Refund refund, String reason) {
        if (refund.isFailed()) {
            return;
        }

        List<RefundItem> refundItems =
                refundItemRepository.findAllByRefundIdWithOrderItem(refund.getId());

        for (RefundItem refundItem : refundItems) {
            refundItem.getOrderItem().releaseRefundQuantity(refundItem.getRefundQuantity());
        }

        refund.fail(reason);
    }

    private RefundOutbox findOutboxForUpdate(Long outboxId) {
        return refundOutboxRepository.findByIdForUpdate(outboxId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
