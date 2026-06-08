package com.teamec2.paymentsystem.domain.refund.entity;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.refund.enums.RefundStatus;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 환불 요청 1건의 대표 기록
 * 환불 생성 시점의 총 환불 금액, 포인트 환불 금액, PG 환불 금액, PortOne 결제 식별자를 스냅샷으로 저장합니다.
 * 이후 주문/결제 정보가 변경되더라도 이미 생성된 환불 내역의 기준 정보는 변경되지 않습니다.
 */
@Getter
@Entity
@Table(
        name = "refunds",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refunds_payment_idempotency_key",
                        columnNames = {"payment_id", "idempotency_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Refund {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 같은 환불 요청이 중복 생성되지 않도록 막는 키
    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "portone_payment_id", nullable = false, length = 100)
    private String portonePaymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // 환불 사유
    @Column(nullable = false, length = 255)
    private String reason;

    // 포인트 + PG 금액을 포함한 총 환불 금액
    @Column(name = "refund_amount", nullable = false)
    private Long refundAmount;

    @Column(name = "point_refund_amount", nullable = false)
    private Long pointRefundAmount;

    // PortOne에 실제 취소 요청할 PG 금액
    @Column(name = "pg_refund_amount", nullable = false)
    private Long pgRefundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;

    // 환불 요청이 생성된 시각
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // PG 취소와 내부 환불 완료 처리가 모두 끝난 시각
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    // 환불 실패 이유
    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    @Column(name = "pg_result_unknown_reason", length = 500)
    private String pgResultUnknownReason;

    private static final int MAX_REASON_LENGTH = 500;

    // 적립 포인트 회수 전, 원래 반환 대상이었던 사용 포인트 금액
    @Column(name = "gross_point_refund_amount", nullable = false)
    private Long grossPointRefundAmount;

    // 적립 포인트 회수 전, 원래 PG로 환불하려던 금액
    @Column(name = "gross_pg_refund_amount", nullable = false)
    private Long grossPgRefundAmount;

    // 이번 환불에서 회수해야 하는 적립 포인트 금액
    @Column(name = "earned_point_recovery_amount", nullable = false)
    private Long earnedPointRecoveryAmount;

    // 반환 예정 사용 포인트에서 상계 처리한 적립 포인트 금액
    @Column(name = "recovered_from_used_point", nullable = false)
    private Long recoveredFromUsedPoint;

    // 고객의 현재 보유 포인트에서 실제 차감할 적립 포인트 금액
    @Column(name = "recovered_from_balance", nullable = false)
    private Long recoveredFromBalance;

    // 보유 포인트로도 회수하지 못해 PG 환불 금액에서 차감한 금액
    @Column(name = "deducted_from_pg_refund", nullable = false)
    private Long deductedFromPgRefund;

    @Column(name = "portone_cancellation_id", unique = true, length = 100)
    private String portoneCancellationId;

    private Refund(
            String idempotencyKey,
            String requestHash,
            Order order,
            Payment payment,
            String reason,
            Long refundAmount,
            Long pointRefundAmount,
            Long pgRefundAmount,
            Long grossPointRefundAmount,
            Long grossPgRefundAmount,
            Long earnedPointRecoveryAmount,
            Long recoveredFromUsedPoint,
            Long recoveredFromBalance,
            Long deductedFromPgRefund
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateRequestHash(requestHash);
        validateRequiredValues(order, payment, reason);
        validateOrderMatchesPayment(order, payment);
        validatePortonePaymentId(payment.getPortonePaymentId());
        validateAmount(refundAmount, pointRefundAmount, pgRefundAmount);
        validateSettlementAmounts(
                grossPointRefundAmount,
                grossPgRefundAmount,
                earnedPointRecoveryAmount,
                recoveredFromUsedPoint,
                recoveredFromBalance,
                deductedFromPgRefund
        );
        validateSettlementConsistency(
                pointRefundAmount,
                pgRefundAmount,
                grossPointRefundAmount,
                grossPgRefundAmount,
                earnedPointRecoveryAmount,
                recoveredFromUsedPoint,
                recoveredFromBalance,
                deductedFromPgRefund
        );

        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.portonePaymentId = payment.getPortonePaymentId();
        this.order = order;
        this.payment = payment;
        this.reason = reason;
        this.refundAmount = refundAmount;
        this.pointRefundAmount = pointRefundAmount;
        this.pgRefundAmount = pgRefundAmount;
        this.status = RefundStatus.PROCESSING;
        this.grossPointRefundAmount = grossPointRefundAmount;
        this.grossPgRefundAmount = grossPgRefundAmount;
        this.earnedPointRecoveryAmount = earnedPointRecoveryAmount;
        this.recoveredFromUsedPoint = recoveredFromUsedPoint;
        this.recoveredFromBalance = recoveredFromBalance;
        this.deductedFromPgRefund = deductedFromPgRefund;
    }

    public static Refund createRefund(
            String idempotencyKey,
            String requestHash,
            Order order,
            Payment payment,
            String reason,
            Long refundAmount,
            Long pointRefundAmount,
            Long pgRefundAmount,
            Long grossPointRefundAmount,
            Long grossPgRefundAmount,
            Long earnedPointRecoveryAmount,
            Long recoveredFromUsedPoint,
            Long recoveredFromBalance,
            Long deductedFromPgRefund
    ) {
        return new Refund(
                idempotencyKey,
                requestHash,
                order,
                payment,
                reason,
                refundAmount,
                pointRefundAmount,
                pgRefundAmount,
                grossPointRefundAmount,
                grossPgRefundAmount,
                earnedPointRecoveryAmount,
                recoveredFromUsedPoint,
                recoveredFromBalance,
                deductedFromPgRefund
        );
    }

    /**
     * PG 취소와 내부 DB 반영이 모두 끝났을 때 호출합니다.
     */
    public void complete(LocalDateTime refundedAt) {
        if (refundedAt == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (this.status != RefundStatus.PROCESSING && this.status != RefundStatus.PG_RESULT_UNKNOWN) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS);
        }

        this.status = RefundStatus.COMPLETED;
        this.refundedAt = refundedAt;
    }

    /**
     * 환불 실패가 확정되었을 때 호출합니다.
     * 1. PROCESSING 상태에서 명확한 실패 응답을 받은 경우
     * 2. PG_RESULT_UNKNOWN 상태에서 재조회 결과 실패가 확정된 경우
     * 이미 FAILED인 경우에는 멱등성을 위해 그대로 return 합니다.
     */
    public void fail(String failedReason) {
        if (this.status == RefundStatus.FAILED) {
            return;
        }

        if (this.status != RefundStatus.PROCESSING && this.status != RefundStatus.PG_RESULT_UNKNOWN) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS);
        }

        if (failedReason == null || failedReason.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        this.status = RefundStatus.FAILED;
        this.failedReason = normalizeReason(failedReason);
    }

    public boolean isProcessing() {
        return this.status == RefundStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return this.status == RefundStatus.COMPLETED;
    }

    public boolean isPgResultUnknown() {
        return this.status == RefundStatus.PG_RESULT_UNKNOWN;
    }

    public boolean isFailed() {
        return this.status == RefundStatus.FAILED;
    }

    /**
     * PortOne 취소 ID를 기록합니다.
     * cancellationId는 PortOne에서 생성한 취소 건 식별자입니다.
     * 같은 결제에서 여러 번 부분 환불이 발생할 수 있으므로,
     * 환불 건 매칭은 portonePaymentId가 아니라 cancellationId 기준으로 합니다.
     */
    public void recordPortoneCancellationId(String portoneCancellationId) {
        if (portoneCancellationId == null || portoneCancellationId.isBlank()) {
            return;
        }

        // 아직 기록된 cancellationId가 없다면 새로 저장합니다.
        if (this.portoneCancellationId == null) {
            this.portoneCancellationId = portoneCancellationId;
            return;
        }

        // 이미 같은 cancellationId가 기록되어 있다면 멱등 처리로 보고 그대로 둡니다.
        if (this.portoneCancellationId.equals(portoneCancellationId)) {
            return;
        }

        /*
         * 이미 다른 cancellationId가 기록되어 있다면 위험한 상태입니다.
         * 같은 Refund가 서로 다른 PortOne 취소 건과 연결되는 상황이므로 막아야 합니다.
         */
        throw new IllegalStateException("이미 다른 PortOne 취소 ID가 기록되어 있습니다.");
    }

    private static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    private static void validateRequiredValues(
            Order order,
            Payment payment,
            String reason
    ) {
        if (order == null || payment == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    private static void validateOrderMatchesPayment(
            Order order,
            Payment payment
    ) {
        if (order.getId() == null || payment.getOrder() == null || payment.getOrder().getId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (!order.getId().equals(payment.getOrder().getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static void validatePortonePaymentId(String portonePaymentId) {
        if (portonePaymentId == null || portonePaymentId.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    private static void validateAmount(
            Long refundAmount,
            Long pointRefundAmount,
            Long pgRefundAmount
    ) {
        if (refundAmount == null || pointRefundAmount == null || pgRefundAmount == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (refundAmount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (pointRefundAmount < 0 || pgRefundAmount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (!refundAmount.equals(pointRefundAmount + pgRefundAmount)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * PG 결과 미확정 처리입니다.
     * 이미 PG_RESULT_UNKNOWN인 경우에는 멱등성을 위해 그대로 return 합니다.
     */
    public void markPgResultUnknown(String pgResultUnknownReason) {
        if (this.status == RefundStatus.PG_RESULT_UNKNOWN) {
            return;
        }

        if (this.status != RefundStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS);
        }

        if (pgResultUnknownReason == null || pgResultUnknownReason.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        this.status = RefundStatus.PG_RESULT_UNKNOWN;
        this.pgResultUnknownReason = normalizeReason(pgResultUnknownReason);
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }

        if (reason.length() <= MAX_REASON_LENGTH) {
            return reason;
        }

        return reason.substring(0, MAX_REASON_LENGTH);
    }

    /**
     * NOTE: 각 금액이 값으로서 유효한가?
     * 환불 정산을 영수증 작성이라고 본다면...
     * => 영수증에 숫자가 비어 있지는 않은지, 마이너스 금액은 없는지 확인하는 단계입니다.
     * 환불 정산 스냅샷에 필요한 금액 값들이 유효한지 검증합니다.
     * 이 메서드는 각 금액 필드가 null이 아니며, 음수가 아닌지만 확인합니다.
     * 즉, "값 자체가 존재하고 기본적으로 말이 되는가"를 검사하는 1차 검증입니다.
     */
    private static void validateSettlementAmounts(
            Long grossPointRefundAmount,
            Long grossPgRefundAmount,
            Long earnedPointRecoveryAmount,
            Long recoveredFromUsedPoint,
            Long recoveredFromBalance,
            Long deductedFromPgRefund
    ) {
        if (grossPointRefundAmount == null
                || grossPgRefundAmount == null
                || earnedPointRecoveryAmount == null
                || recoveredFromUsedPoint == null
                || recoveredFromBalance == null
                || deductedFromPgRefund == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (grossPointRefundAmount < 0
                || grossPgRefundAmount < 0
                || earnedPointRecoveryAmount < 0
                || recoveredFromUsedPoint < 0
                || recoveredFromBalance < 0
                || deductedFromPgRefund < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * NOTE: 금액들끼리 계산 관계가 맞는가?
     * 환불 정산을 영수증 작성이라고 본다면...
     * => 영수증의 계산식이 맞는지 확인하는 단계입니다.
     * 환불 정산 스냅샷의 금액 계산 관계가 일관적인지 검증합니다.
     * 이 메서드는 단순히 금액이 존재하는지를 보는 것이 아니라,
     * 실제 환불 정책에 따라 각 금액이 서로 맞게 계산되었는지 확인합니다.
     * 검증하는 관계는 다음과 같습니다.
     * 1. 사용 포인트에서 회수한 금액은 원래 반환 예정이었던 사용 포인트 금액을 초과할 수 없습니다.
     * 2. PG 환불액에서 차감한 금액은 원래 PG 환불 예정 금액을 초과할 수 없습니다.
     * 3. 최종 포인트 환불액은 원래 포인트 환불 예정액에서 사용 포인트 상계액을 뺀 값이어야 합니다.
     * 4. 최종 PG 환불액은 원래 PG 환불 예정액에서 PG 차감액을 뺀 값이어야 합니다.
     * 5. 회수해야 하는 적립 포인트 총액은 사용 포인트 상계액 + 보유 포인트 차감액 + PG 환불 차감액의 합과 같아야 합니다.
     */
    private static void validateSettlementConsistency(
            Long pointRefundAmount,
            Long pgRefundAmount,
            Long grossPointRefundAmount,
            Long grossPgRefundAmount,
            Long earnedPointRecoveryAmount,
            Long recoveredFromUsedPoint,
            Long recoveredFromBalance,
            Long deductedFromPgRefund
    ) {
        if (recoveredFromUsedPoint > grossPointRefundAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (deductedFromPgRefund > grossPgRefundAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (!pointRefundAmount.equals(grossPointRefundAmount - recoveredFromUsedPoint)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (!pgRefundAmount.equals(grossPgRefundAmount - deductedFromPgRefund)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (!earnedPointRecoveryAmount.equals(
                recoveredFromUsedPoint + recoveredFromBalance + deductedFromPgRefund
        )) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    private static void validateRequestHash(String requestHash) {
        if (requestHash == null || requestHash.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }
}
