package com.teamec2.paymentsystem.domain.refund.enums;

/**
 * 환불 요청 상태입니다.
 *
 * 현재 상태 전이:
 * PROCESSING -> COMPLETED
 * PROCESSING -> FAILED
 * PROCESSING -> PG_RESULT_UNKNOWN
 *
 * PG_RESULT_UNKNOWN -> COMPLETED
 * PG_RESULT_UNKNOWN -> FAILED
 *
 * PG_RESULT_UNKNOWN 상태에서 재시도를 하더라도 Refund 상태 자체를 PROCESSING으로 되돌리지는 않습니다.
 * Outbox만 PENDING으로 돌려 재처리 대상으로 만들고, Refund는 PG 결과를 확정할 때까지 PG_RESULT_UNKNOWN 상태를 유지합니다.
 */
public enum RefundStatus {

    PROCESSING,
    COMPLETED,
    FAILED,

    /**
     * PG 취소 요청 결과를 확정하지 못한 상태입니다.
     * - PortOne API 호출 중 타임아웃 발생
     * - 응답을 받지 못해 실제 취소 성공 여부를 알 수 없음
     * - PROCESSING 상태에서 서버가 중단되어 결과 확인이 필요한 경우
     */
    PG_RESULT_UNKNOWN;
}
