package com.teamec2.paymentsystem.domain.payment.service;

import java.time.LocalDateTime;

/**
 * 외부 결제 성공 후 내부 결제 완료 재시도에 필요한 값입니다.
 *
 * @param paymentId 내부 결제 ID
 * @param approvedAt PortOne 승인 시각
 * @param cancelAmount 완료 불가 상태로 바뀐 경우 보상 취소할 PG 금액
 */
public record PaymentConfirmRetryCommand(
        Long paymentId,
        LocalDateTime approvedAt,
        Long cancelAmount
) {
}
