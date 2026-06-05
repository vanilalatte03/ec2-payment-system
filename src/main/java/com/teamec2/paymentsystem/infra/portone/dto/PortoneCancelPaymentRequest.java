package com.teamec2.paymentsystem.infra.portone.dto;

/**
 * PortOne V2 결제 취소 API 요청 본문.
 *
 * <p>이 DTO는 infra 계층에서만 사용하는 PortOne 전용 형식이다.
 * 도메인 서비스는 이 구조를 직접 만들지 않고 {@code PaymentGateway.cancelPayment(...)}만 호출한다.
 *
 * @param storeId PortOne 상점 ID
 * @param amount 취소할 금액
 * @param reason 취소 사유
 * @param requester 취소 요청 주체. 보상 취소는 서버가 수행하므로 {@code ADMIN}을 사용한다.
 * @param currentCancellableAmount PortOne이 가진 취소 가능 금액과 우리 요청 값이 일치하는지 검증하기 위한 값
 */
public record PortoneCancelPaymentRequest(
        String storeId,
        Long amount,
        String reason,
        String requester,
        Long currentCancellableAmount
) {
}
