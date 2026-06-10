package com.teamec2.paymentsystem.domain.payment.entity;

import com.teamec2.paymentsystem.domain.payment.enums.PaymentCompensationOutboxStatus;
import com.teamec2.paymentsystem.global.entity.BaseEntity;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Getter
@Entity
@Table(
        name = "payment_compensation_outbox",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_compensation_outbox_payment",
                        columnNames = "payment_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCompensationOutbox extends BaseEntity {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long BASE_RETRY_DELAY_MINUTES = 5L;
    private static final long MAX_RETRY_DELAY_MINUTES = 60L;
    private static final long MAX_JITTER_SECONDS = 60L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "cancel_amount", nullable = false)
    private Long cancelAmount;

    @Column(name = "portone_cancellation_id", length = 100)
    private String portoneCancellationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentCompensationOutboxStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    private LocalDateTime processingStartedAt;

    @Column(length = MAX_ERROR_MESSAGE_LENGTH)
    private String lastErrorMessage;

    public static PaymentCompensationOutbox create(Payment payment, Long cancelAmount, String reason, LocalDateTime now) {
        if (payment == null || cancelAmount == null || now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (cancelAmount <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        PaymentCompensationOutbox outbox = new PaymentCompensationOutbox();
        outbox.payment = payment;
        outbox.cancelAmount = cancelAmount;
        outbox.status = PaymentCompensationOutboxStatus.PENDING;
        outbox.retryCount = 0;
        outbox.nextAttemptAt = now;
        outbox.lastErrorMessage = outbox.normalizeErrorMessage(reason);
        return outbox;
    }

    public void markPending(String reason, LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (status == PaymentCompensationOutboxStatus.SUCCEEDED) {
            return;
        }

        if (status == PaymentCompensationOutboxStatus.FAILED) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.status = PaymentCompensationOutboxStatus.PENDING;
        this.nextAttemptAt = now;
        this.processingStartedAt = null;
        this.lastErrorMessage = normalizeErrorMessage(reason);
    }

    public void markProcessing(LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (status != PaymentCompensationOutboxStatus.PENDING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.status = PaymentCompensationOutboxStatus.PROCESSING;
        this.processingStartedAt = now;
    }

    public void recordPortoneCancellationId(String portoneCancellationId) {
        this.portoneCancellationId = portoneCancellationId;
    }

    public void markSucceeded() {
        if (status == PaymentCompensationOutboxStatus.SUCCEEDED) {
            return;
        }

        if (status != PaymentCompensationOutboxStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.status = PaymentCompensationOutboxStatus.SUCCEEDED;
        this.processingStartedAt = null;
        this.lastErrorMessage = null;
    }

    public boolean markRetry(String reason, LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (status != PaymentCompensationOutboxStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.retryCount++;

        if (retryCount > MAX_RETRY_COUNT) {
            markFailed("최대 재시도 횟수를 초과했습니다. 마지막 오류: " + reason);
            return false;
        }

        this.status = PaymentCompensationOutboxStatus.PENDING;
        this.lastErrorMessage = normalizeErrorMessage(reason);
        this.nextAttemptAt = calculateNextAttemptAt(now, retryCount);
        this.processingStartedAt = null;
        return true;
    }

    public void markFailed(String reason) {
        if (status == PaymentCompensationOutboxStatus.FAILED) {
            return;
        }

        if (status != PaymentCompensationOutboxStatus.PENDING
                && status != PaymentCompensationOutboxStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        this.status = PaymentCompensationOutboxStatus.FAILED;
        this.lastErrorMessage = normalizeErrorMessage(reason);
        this.processingStartedAt = null;
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
}
