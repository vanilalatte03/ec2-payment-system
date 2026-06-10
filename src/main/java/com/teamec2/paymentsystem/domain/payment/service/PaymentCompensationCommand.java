package com.teamec2.paymentsystem.domain.payment.service;

/**
 * 결제 확정 보상 취소 PG 호출에 필요한 값입니다.
 *
 * @param outboxId 보상 취소 Outbox ID
 * @param paymentId 내부 결제 ID
 * @param portonePaymentId PortOne 결제 ID
 * @param cancelAmount 취소할 PG 결제 금액
 * @param currentCancellableAmount 현재 취소 가능 금액
 * @param reason 취소 사유
 * @param idempotencyKey PG 취소 멱등 키
 */
public record PaymentCompensationCommand(
        Long outboxId,
        Long paymentId,
        String portonePaymentId,
        Long cancelAmount,
        Long currentCancellableAmount,
        String reason,
        String idempotencyKey
) {
}
