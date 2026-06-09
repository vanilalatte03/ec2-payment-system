package com.teamec2.paymentsystem.domain.refund.enums;

/**
 * 환불 Outbox 작업 상태입니다.
 *
 * PENDING:
 * - 아직 스케줄러가 처리하지 않은 작업
 * - 또는 재시도 예약 시간이 될 때까지 대기 중인 작업
 *
 * PROCESSING:
 * - 스케줄러가 작업을 선점했고, PG 취소 요청 또는 후속 처리를 진행 중인 상태
 *
 * SUCCEEDED:
 * - PG 취소와 내부 DB 반영이 모두 끝난 작업
 *
 * FAILED:
 * - 더 이상 재시도하지 않을 실패 작업
 */
public enum RefundOutboxStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED;
}
