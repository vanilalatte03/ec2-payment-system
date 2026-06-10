package com.teamec2.paymentsystem.domain.payment.entity;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.global.entity.BaseEntity;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 주문에 대한 결제 정보를 관리하는 엔티티.
 *
 * <p>결제는 주문 생성 시 PENDING 상태로 먼저 생성되고, PortOne 결제 검증 결과에 따라
 * 완료, 실패 또는 환불 상태로 전이된다.
 */
@Entity
@Getter
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int MAX_CONFIRM_RETRY_COUNT = 5;
    private static final long BASE_RETRY_DELAY_MINUTES = 5L;
    private static final long MAX_RETRY_DELAY_MINUTES = 60L;
    private static final long MAX_JITTER_SECONDS = 60L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "portone_payment_id", nullable = false, unique = true, length = 100)
    private String portonePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 30)
    private PaymentType paymentType;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long usedPointAmount;

    @Column(nullable = false)
    private Long pgAmount;

    @Column(nullable = false)
    private Long rewardPointAmount;

    @Column(name = "cart_cleared", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean cartCleared = false;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

    @Column(name = "confirm_retry_count", nullable = false)
    private int confirmRetryCount = 0;

    @Column(name = "next_confirm_attempt_at")
    private LocalDateTime nextConfirmAttemptAt;

    @Column(name = "confirm_processing_started_at")
    private LocalDateTime confirmProcessingStartedAt;

    @Column(name = "confirm_retry_approved_at")
    private LocalDateTime confirmRetryApprovedAt;

    @Column(name = "confirm_last_error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String confirmLastErrorMessage;

    private Payment(
            Order order,
            String portonePaymentId,
            Long totalAmount,
            Long usedPointAmount,
            Long pgAmount,
            Long rewardPointAmount
    ) {
        if (order == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        validateAmounts(totalAmount, usedPointAmount, pgAmount, rewardPointAmount);

        this.order = order;
        this.portonePaymentId = portonePaymentId;
        this.status = PaymentStatus.PENDING;
        this.paymentType = PaymentType.from(usedPointAmount, pgAmount);
        this.totalAmount = totalAmount;
        this.usedPointAmount = usedPointAmount;
        this.pgAmount = pgAmount;
        this.rewardPointAmount = rewardPointAmount;
    }

    /**
     * 결제 확정 전 대기 상태의 결제 레코드를 생성한다.
     *
     * <p>결제 타입은 사용 포인트와 PG 결제 금액을 기준으로 계산한다.
     *
     * @param totalAmount 총 결제 금액
     * @param usedPointAmount 사용 포인트 금액
     * @param pgAmount PG 결제 금액
     * @param rewardPointAmount 적립 예정 포인트
     * @return PENDING 상태의 결제 엔티티
     */
    public static Payment createPending(
            Order order,
            Long totalAmount,
            Long usedPointAmount,
            Long pgAmount,
            Long rewardPointAmount
    ) {
        return new Payment(
                order,
                generatePortonePaymentId(),
                totalAmount,
                usedPointAmount,
                pgAmount,
                rewardPointAmount
        );
    }

    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean canConfirm() {
        return status == PaymentStatus.PENDING
                || status == PaymentStatus.CONFIRM_RETRY_REQUIRED;
    }

    public boolean isConfirmRetryRequired() {
        return status == PaymentStatus.CONFIRM_RETRY_REQUIRED;
    }

    public boolean isCompensationRequired() {
        return status == PaymentStatus.COMPENSATION_REQUIRED;
    }

    public boolean isPointOnly() {
        return pgAmount == 0;
    }

    /**
     * 결제 검증 성공 후 결제를 완료 상태로 변경한다.
     *
     * @param approvedAt 결제 승인 일시
     */
    public void complete(LocalDateTime approvedAt) {
        if (approvedAt == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        changeStatus(PaymentStatus.COMPLETED);
        this.approvedAt = approvedAt;
        clearConfirmRetryState();
    }

    /**
     * 결제 완료 시점에 장바구니 상품 삭제가 실제로 일어났는지 기록한다.
     *
     * @param cartCleared 주문에 포함된 장바구니 상품이 1건 이상 삭제됐는지 여부
     */
    public void recordCartCleared(boolean cartCleared) {
        this.cartCleared = cartCleared;
    }

    /**
     * 결제 검증 또는 승인 실패 후 결제를 실패 상태로 변경한다.
     *
     * @param failedAt 결제 실패 일시
     */
    public void fail(LocalDateTime failedAt) {
        if (failedAt == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        changeStatus(PaymentStatus.FAILED);
        this.failedAt = failedAt;
        clearConfirmRetryState();
    }

    /**
     * 외부 결제 성공은 확인했지만 내부 완료 트랜잭션이 실패한 경우 재시도 대상으로 남긴다.
     */
    public void markConfirmRetryRequired(LocalDateTime approvedAt, String reason, LocalDateTime now) {
        if (approvedAt == null || now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (isCompleted()) {
            return;
        }

        if (status != PaymentStatus.CONFIRM_RETRY_REQUIRED) {
            changeStatus(PaymentStatus.CONFIRM_RETRY_REQUIRED);
            this.confirmRetryCount = 0;
        }

        this.confirmRetryApprovedAt = approvedAt;
        this.nextConfirmAttemptAt = now;
        this.confirmProcessingStartedAt = null;
        this.confirmLastErrorMessage = normalizeErrorMessage(reason);
    }

    public void markConfirmProcessing(LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (status != PaymentStatus.CONFIRM_RETRY_REQUIRED) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.confirmProcessingStartedAt = now;
    }

    public boolean markConfirmRetry(String reason, LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (status != PaymentStatus.CONFIRM_RETRY_REQUIRED) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.confirmRetryCount++;
        this.confirmLastErrorMessage = normalizeErrorMessage(reason);
        this.confirmProcessingStartedAt = null;

        if (this.confirmRetryCount > MAX_CONFIRM_RETRY_COUNT) {
            this.nextConfirmAttemptAt = null;
            return false;
        }

        this.nextConfirmAttemptAt = calculateNextAttemptAt(now, confirmRetryCount);
        return true;
    }

    /**
     * 외부 결제가 성공했지만 내부 주문으로 완료하면 안 되는 경우 PG 보상 취소 대상으로 남긴다.
     */
    public void markCompensationRequired() {
        if (status != PaymentStatus.COMPENSATION_REQUIRED) {
            changeStatus(PaymentStatus.COMPENSATION_REQUIRED);
        }

        clearConfirmRetryState();
    }

    public void markCompensationFailed() {
        if (status == PaymentStatus.COMPENSATION_FAILED) {
            return;
        }

        changeStatus(PaymentStatus.COMPENSATION_FAILED);
    }

    /**
     * 결제 대기 상태인지 확인하고, 금액 합계가 맞는지 검증하고
     * 결제 금액들을 갱신하고 결제 타입도 새 금액 기준으로 다시 계산한다.
     *
     * @param totalAmount 총 주문 금액
     * @param usedPointAmount 사용 포인트
     * @param pgAmount PG 결제 금액
     * @param rewardPointAmount 적립 예정 포인트
     */
    public void updatePendingAmounts(
            Long totalAmount,
            Long usedPointAmount,
            Long pgAmount,
            Long rewardPointAmount
    ) {
        if (!isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        validateAmounts(totalAmount, usedPointAmount, pgAmount, rewardPointAmount);

        this.totalAmount = totalAmount;
        this.usedPointAmount = usedPointAmount;
        this.pgAmount = pgAmount;
        this.rewardPointAmount = rewardPointAmount;
        this.paymentType = PaymentType.from(usedPointAmount, pgAmount);
    }

    /**
     * 결제를 부분 환불 상태로 변경한다.
     */
    public void markAsPartialRefunded() {
        if (this.status == PaymentStatus.PARTIAL_REFUNDED) {
            return;
        }

        changeStatus(PaymentStatus.PARTIAL_REFUNDED);
    }

    /**
     * 결제를 전액 환불 상태로 변경한다.
     */
    public void markAsRefunded() {
        changeStatus(PaymentStatus.FULL_REFUNDED);
    }

    private void changeStatus(PaymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        this.status = newStatus;
    }

    private static String generatePortonePaymentId() {
        return "pay_" + UUID.randomUUID();
    }

    private void clearConfirmRetryState() {
        this.confirmRetryCount = 0;
        this.nextConfirmAttemptAt = null;
        this.confirmProcessingStartedAt = null;
        this.confirmRetryApprovedAt = null;
        this.confirmLastErrorMessage = null;
    }

    private static LocalDateTime calculateNextAttemptAt(LocalDateTime now, int retryCount) {
        long delayMinutes = Math.min(
                (long) (BASE_RETRY_DELAY_MINUTES * Math.pow(2, retryCount - 1)),
                MAX_RETRY_DELAY_MINUTES
        );

        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, MAX_JITTER_SECONDS + 1);

        return now.plusMinutes(delayMinutes).plusSeconds(jitterSeconds);
    }

    private String normalizeErrorMessage(String reason) {
        if (reason == null) {
            return null;
        }

        if (reason.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return reason;
        }

        return reason.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private static void validateAmounts(
            Long totalAmount,
            Long usedPointAmount,
            Long pgAmount,
            Long rewardPointAmount
    ) {
        if (totalAmount == null || usedPointAmount == null || pgAmount == null || rewardPointAmount == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (totalAmount < 0 || usedPointAmount < 0 || pgAmount < 0 || rewardPointAmount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (!totalAmount.equals(usedPointAmount + pgAmount)) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }
}
