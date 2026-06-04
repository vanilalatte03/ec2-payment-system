package com.teamec2.paymentsystem.domain.point.enums;

public enum PointTransactionType {

    USE_RESERVE, // 결제 PENDING 중 포인트 예약
    USE, // 포인트 사용
    USE_CANCEL, // 주문 CANCELED 혹은 결제 FAILED 로 예약 해제
    EARN, // 포인트 적립
    USE_RESTORE, // 환불 시 사용한 포인트 복구
    EARN_CANCEL; // 환불 시 적립한 포인트 회수

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
                || this == EARN_CANCEL;
    }
}
