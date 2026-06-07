package com.teamec2.paymentsystem.domain.point.enums;

public enum PointTransactionType {

    USE_RESERVE, // 결제 PENDING 중 포인트 예약
    USE, // 포인트 사용
    USE_CANCEL, // 주문 CANCELED 혹은 결제 FAILED 로 예약 해제

    EARN, // 포인트 적립

    USE_RESTORE, // 환불 시 사용한 포인트 복구
    EARN_CANCEL, // 환불 시 적립한 포인트 회수

    /**
     * 환불 요청 시점에 적립 포인트 회수 예정 금액을 미리 차감합니다.
     * PG 환불이 아직 완료되지 않았기 때문에 "최종 회수"가 아니라 "예약 회수" 의미입니다.
     */
    EARN_RECOVERY_RESERVE,

    /**
     * PG 환불이 최종 실패했을 때,
     * 예약 차감했던 적립 포인트를 다시 돌려줍니다.
     */
    EARN_RECOVERY_RELEASE;

    /**
     * 결제 과정에서 발생하는 포인트 거래 유형인지 확인합니다.
     */
    public boolean isPaymentType() {
        return this == USE_RESERVE
                || this == USE
                || this == USE_CANCEL
                || this == EARN;
    }

    /**
     * 환불 과정에서 발생하는 포인트 거래 유형인지 확인합니다.
     */
    public boolean isRefundType() {
        return this == USE_RESTORE
                || this == EARN_CANCEL
                || this == EARN_RECOVERY_RESERVE
                || this == EARN_RECOVERY_RELEASE;
    }


}
