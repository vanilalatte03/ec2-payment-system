package com.teamec2.paymentsystem.domain.refund.enums;

/**
 * PROCESSING -> COMPLETED
 * PROCESSING -> FAILED
 * PROCESSING -> PG_RESULT_UNKNOWN
 * PG_RESULT_UNKNOWN -> COMPLETED
 * PG_RESULT_UNKNOWN -> FAILED
 * PG_RESULT_UNKNOWN -> PROCESSING
 */
public enum RefundStatus {

    PROCESSING,
    COMPLETED,
    FAILED,

    /**
     * PG 취소 요청 결과를 확정하지 못한 상태입니다.
     *
     * 예를 들어 PortOne API 호출 중 타임아웃이 발생했거나,
     * 응답을 받지 못해 실제 취소 성공 여부를 알 수 없는 경우 사용합니다.
     */
    PG_RESULT_UNKNOWN;
}
