package com.teamec2.paymentsystem.domain.payment.port;

/**
 * 외부 결제 취소 결과를 우리 서비스 기준으로 해석한 상태.
 *
 * <p>PortOne의 원본 상태 문자열을 환불 도메인 서비스가 직접 해석하지 않도록
 * 결제 게이트웨이 port의 표준 취소 상태로 분리한다.
 */
public enum PaymentCancelStatus {
    /**
     * 외부 결제 취소가 최종 성공한 상태입니다.
     */
    SUCCEEDED,

    /**
     * 외부 결제 취소가 명확히 실패한 상태입니다.
     */
    FAILED,

    /**
     * 외부 결제 취소 결과가 아직 성공/실패로 확정되지 않은 상태입니다.
     *
     * 예:
     * - PortOne REQUESTED
     * - 외부 API가 알 수 없는 상태를 반환한 경우
     * - 추후 재조회 또는 재시도가 필요한 상태
     */
    RESULT_UNKNOWN
}
