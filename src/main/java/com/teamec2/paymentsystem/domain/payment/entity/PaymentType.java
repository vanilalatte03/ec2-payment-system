package com.teamec2.paymentsystem.domain.payment.entity;

/**
 * 결제 금액 구성에 따른 결제 타입.
 */
public enum PaymentType {
    /** PG 결제만 사용하는 카드 결제 */
    CARD,

    /** 포인트만 사용하는 전액 포인트 결제 */
    POINT_ONLY,

    /** 포인트와 PG 결제를 함께 사용하는 복합 결제 */
    POINT_CARD;

    /**
     * 사용 포인트와 PG 결제 금액을 기준으로 결제 타입을 계산한다.
     *
     * @param usedPointAmount 사용 포인트 금액
     * @param pgAmount PG 결제 금액
     * @return 결제 금액 구성에 맞는 결제 타입
     */
    public static PaymentType from(Long usedPointAmount, Long pgAmount) {
        if (pgAmount == 0) {
            return POINT_ONLY;
        }

        if (usedPointAmount == 0) {
            return CARD;
        }

        return POINT_CARD;
    }
}
