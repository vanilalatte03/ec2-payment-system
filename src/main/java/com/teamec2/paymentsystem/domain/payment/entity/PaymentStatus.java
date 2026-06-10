package com.teamec2.paymentsystem.domain.payment.entity;

/**
 * 결제 처리 상태.
 *
 * <p>결제는 PENDING 상태로 생성되며, 결제 검증 결과에 따라 COMPLETED 또는 FAILED로 전이된다.
 * 외부 결제는 성공했지만 내부 완료/보상 취소가 끝나지 않은 경우에는 재처리 상태로 남긴다.
 * COMPLETED 이후에는 환불 처리 결과에 따라 PARTIAL_REFUNDED 또는 REFUNDED로 전이될 수 있다.
 */
public enum PaymentStatus {
    /** 결제 검증 전 대기 상태 */
    PENDING,

    /** 외부 결제 성공 후 내부 완료 처리를 다시 시도해야 하는 상태 */
    CONFIRM_RETRY_REQUIRED,

    /** 외부 결제를 완료하면 안 되어 PG 보상 취소를 다시 시도해야 하는 상태 */
    COMPENSATION_REQUIRED,

    /** PG 보상 취소 자동 재시도가 실패해 운영자 확인이 필요한 상태 */
    COMPENSATION_FAILED,

    /** 결제 승인 완료 상태 */
    COMPLETED,

    /** 결제 검증 또는 승인 실패 상태 */
    FAILED,

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
                    || target == CONFIRM_RETRY_REQUIRED
                    || target == COMPENSATION_REQUIRED;
            case CONFIRM_RETRY_REQUIRED -> target == COMPLETED || target == COMPENSATION_REQUIRED;
            case COMPENSATION_REQUIRED -> target == FAILED || target == COMPENSATION_FAILED;
            case COMPLETED -> target == PARTIAL_REFUNDED || target == FULL_REFUNDED;
            case PARTIAL_REFUNDED -> target == FULL_REFUNDED;
            case COMPENSATION_FAILED, FAILED, FULL_REFUNDED -> false;
        };
    }
}
