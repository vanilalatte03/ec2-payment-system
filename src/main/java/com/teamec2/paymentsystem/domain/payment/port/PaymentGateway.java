package com.teamec2.paymentsystem.domain.payment.port;

import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;

/**
 * 결제 도메인이 외부 결제 시스템과 통신할 때 사용하는 포트.
 *
 * <p>도메인 서비스는 PortOne의 HTTP API 형식이나 SDK 세부 사항을 직접 알 필요가 없다.
 * 이 인터페이스를 통해 "결제 조회"와 "결제 취소"라는 도메인 관점의 기능만 호출한다.
 */
public interface PaymentGateway {

    /**
     * 외부 결제 시스템에서 결제 단건 정보를 조회한다.
     *
     * <p>결제 확정 API와 웹훅 처리는 클라이언트나 웹훅 본문을 최종 신뢰하지 않고,
     * 이 조회 결과를 기준으로 실제 결제 성공 여부와 승인 금액을 검증한다.
     *
     * @param paymentId PortOne 결제 ID
     * @return 외부 결제 조회 결과
     */
    PaymentGatewayResponse getPayment(String paymentId);

    /**
     * 외부 결제 시스템에 결제 취소를 요청한다.
     *
     * <p>현재 사용 목적은 사용자 환불이 아니라 결제 확정 보상 취소다.
     * 즉, PortOne 결제는 성공했지만 내부 DB 완료 처리가 실패했을 때 외부 결제를 되돌리기 위해 호출한다.
     *
     * @param paymentId PortOne 결제 ID
     * @param cancelAmount 취소할 금액
     * @param reason 취소 사유
     * @param idempotencyKey 같은 취소 요청이 중복 처리되지 않도록 사용하는 멱등 키
     * @return 외부 결제 취소 결과
     */
    PaymentCancelResponse cancelPayment(
            String paymentId,
            Long cancelAmount,
            String reason,
            String idempotencyKey
    );
}
