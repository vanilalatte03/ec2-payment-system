package com.teamec2.paymentsystem.domain.refund.service;

/**
 * 환불 금액 계산 결과입니다.
 *
 * @param requestedRefundAmount 상품 기준 이번 환불 요청 금액
 * @param refundAmount 고객에게 실제로 환불되는 최종 금액 (pointRefundAmount + pgRefundAmount)
 * @param grossPointRefundAmount 적립 포인트 회수 전, 원래 반환 예정이었던 사용 포인트 금액
 * @param grossPgRefundAmount 적립 포인트 회수 전, 원래 PG로 환불하려던 금액
 * @param earnedPointRecoveryAmount 이번 환불에서 회수해야 하는 적립 포인트 금액
 * @param pointRefundAmount 고객에게 실제 반환할 사용 포인트 금액
 * @param pgRefundAmount PortOne에 실제 취소 요청할 PG 환불 금액
 * @param recoveredFromUsedPoint 반환 예정 사용 포인트에서 상계한 적립 포인트 금액
 * @param recoveredFromBalance 고객의 현재 보유 포인트 잔액에서 차감할 적립 포인트 금액
 * @param deductedFromPgRefund 보유 포인트로도 회수하지 못해 PG 환불 금액에서 차감한 금액
 */
public record RefundAmount (
        long requestedRefundAmount,
        long refundAmount,
        long grossPointRefundAmount,
        long grossPgRefundAmount,
        long earnedPointRecoveryAmount,
        long pointRefundAmount,
        long pgRefundAmount,
        long recoveredFromUsedPoint,
        long recoveredFromBalance,
        long deductedFromPgRefund
){
}
