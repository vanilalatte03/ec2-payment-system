package com.teamec2.paymentsystem.domain.payment.entity;

/**
 * 결제 처리 상태.
 *
 * <p>결제는 PENDING 상태로 생성되며, 결제 검증 결과에 따라 COMPLETED 또는 FAILED로 전이된다.
 * 외부 결제 성공 후 내부 확정 실패 보상 취소가 애매하게 끝난 경우에는 보상 정리 전용 상태로 남긴다.
 * COMPLETED 이후에는 환불 처리 결과에 따라 PARTIAL_REFUNDED 또는 REFUNDED로 전이될 수 있다.
 */
public enum PaymentStatus {
    /** 결제 검증 전 대기 상태 */
    PENDING,

    /** 결제 승인 완료 상태 */
    COMPLETED,

    /** 결제 검증 또는 승인 실패 상태 */
    FAILED,

    /** PortOne 보상 취소는 성공했고 내부 주문/결제 실패 정리만 남은 상태 */
    COMPENSATION_REQUIRED,

    /** PortOne 보상 취소 요청 결과를 확정하지 못해 재조회/운영 확인이 필요한 상태 */
    COMPENSATION_RESULT_UNKNOWN,

    /** 일부 금액이 환불된 상태 */
    PARTIAL_REFUNDED,

    /** 전체 금액이 환불된 상태 */
    FULL_REFUNDED;

    /**
     * 현재 상태에서 대상 상태로 전이할 수 있는지 확인한다.
     *
     * @param target 전이하려는 대상 상태
     * @return 전이 가능 여부
     */
    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PENDING -> target == COMPLETED
                    || target == FAILED
                    || target == COMPENSATION_REQUIRED
                    || target == COMPENSATION_RESULT_UNKNOWN;
            case COMPENSATION_REQUIRED -> target == FAILED;
            case COMPENSATION_RESULT_UNKNOWN -> target == COMPENSATION_REQUIRED || target == FAILED;
            case COMPLETED -> target == PARTIAL_REFUNDED || target == FULL_REFUNDED;
            case PARTIAL_REFUNDED -> target == FULL_REFUNDED;
            case FAILED, FULL_REFUNDED -> false;
        };
    }
}
