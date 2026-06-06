package com.teamec2.paymentsystem.domain.refund.entity;

import com.teamec2.paymentsystem.domain.refund.enums.RefundOutboxStatus;
import com.teamec2.paymentsystem.global.entity.BaseEntity;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 환불을 실제 PG사에 보낼 작업표입니다.
 *
 * RefundOutbox는 환불 자체가 아니라,
 * "이 환불을 PG사에 보내서 처리해야 한다"는 작업 큐 역할을 합니다.
 *
 * status:
 * - PENDING: 처리 대기
 * - PROCESSING: 처리 중
 * - SUCCEEDED: 처리 성공
 * - FAILED: 처리 실패
 *
 * retryCount, nextAttemptAt을 통해 실패한 작업을 일정 시간 뒤 재시도할 수 있습니다.
 */
@Getter
@Entity
@Table(
        name = "refund_outbox",
        uniqueConstraints = {
            @UniqueConstraint(
                name = "uk_refund_outbox_refund",
                columnNames = "refund_id"
            )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundOutbox extends BaseEntity {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    /**
     * 최대 재시도 예약 횟수입니다.
     *
     * retryCount가 1~5까지는 재시도 예약을 허용하고,
     * 6번째 실패 시 FAILED로 전환됩니다.
     */
    private static final int MAX_RETRY_COUNT = 5;

    private static final long BASE_RETRY_DELAY_MINUTES = 5L;
    private static final long MAX_RETRY_DELAY_MINUTES = 60L;
    private static final long MAX_JITTER_SECONDS = 60L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 처리할 환불 1건입니다.
     * outbox 1건은 refund 1건과 연결됩니다.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    private Refund refund;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundOutboxStatus status;

    /**
     * 재시도 횟수
     * 최대 5번 재시도 합니다.
     */
    @Column(nullable = false)
    private int retryCount;

    /**
     * 다음 재시도 가능 시각입니다.
     *
     * 실패한 작업을 바로 재시도하지 않고,
     * exponential backoff + jitter 방식으로 재시도 시각을 늦춥니다.
     */
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    /**
     * PROCESSING 상태로 진입한 시각입니다.
     *
     * 서버가 PG 호출 직전/직후에 죽으면 Outbox가 PROCESSING 상태에 갇힐 수 있으므로,
     * 오래된 PROCESSING 작업을 찾기 위해 사용합니다.
     */
    private LocalDateTime processingStartedAt;

    /**
     * 마지막 실패 사유입니다.
     * 이 필드는 운영자가 빠르게 확인할 수 있는 요약 메시지 용도로 사용합니다.
     */
    @Column(length = MAX_ERROR_MESSAGE_LENGTH)
    private String lastErrorMessage;

    public static RefundOutbox create(Refund refund, LocalDateTime now) {
        if (refund == null || now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        RefundOutbox outbox = new RefundOutbox();
        outbox.refund = refund;
        outbox.status = RefundOutboxStatus.PENDING;
        outbox.retryCount = 0;
        outbox.nextAttemptAt = now;
        return outbox;
    }

    /**
     * 스케줄러가 작업을 실제로 처리하기 직전에 호출합니다.
     * PENDING -> PROCESSING
     */
    public void markProcessing(LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (this.status != RefundOutboxStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
        }

        this.status = RefundOutboxStatus.PROCESSING;
        this.processingStartedAt = now;
    }


    /**
     * PG 환불이 성공하고 내부 DB 반영까지 완료된 후 호출합니다.
     * PROCESSING -> SUCCEEDED
     * 이미 SUCCEEDED인 경우에는 멱등성을 위해 그대로 return 합니다.
     */
    public void markSucceeded() {
        if (this.status == RefundOutboxStatus.SUCCEEDED) {
            return;
        }

        if (this.status != RefundOutboxStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
        }

        this.status = RefundOutboxStatus.SUCCEEDED;
        this.processingStartedAt = null;
    }

    /**
     * PG 호출 실패 또는 PG 결과 불명확 상황에서 재시도를 예약합니다.
     * PROCESSING -> PENDING
     */
    public void markRetry(String reason, LocalDateTime now) {
        if (now == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (this.status != RefundOutboxStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
        }

        this.retryCount++;

        if (this.retryCount > MAX_RETRY_COUNT) {
            markFailed("최대 재시도 횟수를 초과했습니다. 마지막 오류: " + reason);
            return;
        }

        this.status = RefundOutboxStatus.PENDING;
        this.lastErrorMessage = normalizeErrorMessage(reason);

        long delayMinutes = Math.min(
                (long) (BASE_RETRY_DELAY_MINUTES * Math.pow(2, retryCount - 1)),
                MAX_RETRY_DELAY_MINUTES);

        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, MAX_JITTER_SECONDS + 1);

        this.nextAttemptAt = now.plusMinutes(delayMinutes).plusSeconds(jitterSeconds);
        this.processingStartedAt = null;
    }

    /**
     * 더 이상 재시도하지 않을 실패 상황에서 호출합니다.
     * PENDING -> FAILED
     * PROCESSING -> FAILED
     * 이미 FAILED인 경우에는 멱등성을 위해 그대로 return 합니다.
     */
    public void markFailed(String reason) {
        if (this.status == RefundOutboxStatus.FAILED) {
            return;
        }

        if (this.status != RefundOutboxStatus.PENDING
                && this.status != RefundOutboxStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_OUTBOX_STATUS);
        }

        this.status = RefundOutboxStatus.FAILED;
        this.lastErrorMessage = normalizeErrorMessage(reason);
        this.processingStartedAt = null;
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
