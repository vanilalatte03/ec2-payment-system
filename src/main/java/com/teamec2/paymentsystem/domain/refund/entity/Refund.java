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
 * 환불 요청 1건의 상품별 상세 기록
 * 환불 생성 시점의 주문 상품 단가와 환불 금액을 스냅샷으로 저장합니다.
 * 이후 OrderItem 또는 상품 가격 정책이 변경되더라도
 * 이미 생성된 환불 내역의 금액은 변경되지 않습니다.
 */
@Getter
@Entity
@Table(
        name = "refunds", uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refunds_idempotency_key",
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

    private Refund(
            String idempotencyKey,
            Order order,
            Payment payment,
            String reason,
            Long refundAmount,
            Long pointRefundAmount,
            Long pgRefundAmount
    ) {
        validateIdempotencyKey(idempotencyKey);
        validateRequiredValues(order, payment, reason);
        validateOrderMatchesPayment(order, payment);
        validateAmount(refundAmount, pointRefundAmount, pgRefundAmount);

        this.idempotencyKey = idempotencyKey;
        this.order = order;
        this.payment = payment;
        this.reason = reason;
        this.refundAmount = refundAmount;
        this.pointRefundAmount = pointRefundAmount;
        this.pgRefundAmount = pgRefundAmount;
        this.status = RefundStatus.PROCESSING;
    }

    public static Refund createRefund(
            String idempotencyKey,
            Order order,
            Payment payment,
            String reason,
            Long refundAmount,
            Long pointRefundAmount,
            Long pgRefundAmount
    ) {
        return new Refund(idempotencyKey, order, payment, reason, refundAmount, pointRefundAmount, pgRefundAmount);
    }

    /**
     * PG 취소와 내부 DB 반영이 모두 끝났을 때 호출합니다.
     */
    public void complete(LocalDateTime refundedAt) {

        if (refundedAt == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (this.status != RefundStatus.PROCESSING && this.status != RefundStatus.PG_RESULT_UNKNOWN) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.status = RefundStatus.COMPLETED;
        this.refundedAt = refundedAt;
    }

    /**
     * PG_RESULT_UNKNOWN -> COMPLETED 상황
     * 1. PortOne 조회 결과 실제 취소가 안 된 것이 확인됨
     * 2. 재시도 횟수를 초과함
     * 3. 결제 상태상 더 이상 취소할 수 없음
     * 4. 취소 가능 금액 초과 등 명확한 실패 응답을 받음

     */
    public void fail(String failedReason) {

        if (this.status != RefundStatus.PROCESSING && this.status != RefundStatus.PG_RESULT_UNKNOWN) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        if (failedReason == null || failedReason.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        this.status = RefundStatus.FAILED;
        this.failedReason = failedReason;
    }

    public boolean isProcessing() {
        return this.status == RefundStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return this.status == RefundStatus.COMPLETED;
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
     * PG 결과 미확정 처리
     * PortOne 환불 API 호출
     * → 타임아웃 발생
     * → 실제 취소 성공 여부 모름
     */
    public void markPgResultUnknown(String pgResultUnknownReason) {

        if (this.status != RefundStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        this.status = RefundStatus.PG_RESULT_UNKNOWN;
        this.pgResultUnknownReason = reason;
    }
}
