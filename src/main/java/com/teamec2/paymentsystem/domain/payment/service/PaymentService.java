package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 도메인의 PG 조회와 검증을 담당하는 서비스.
 *
 * <p>유스케이스 순서는 Facade가 조율하고, 이 클래스는 PortOne 같은 외부 결제 시스템과의
 * 도메인 규칙만 처리한다. DB 상태 변경은 {@link PaymentConfirmTxService}에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentGateway paymentGateway;

    /**
     * PortOne 결제 단건 정보를 조회한다.
     *
     * @param portonePaymentId PortOne 결제 ID
     * @return 외부 결제 조회 결과
     */
    public PaymentGatewayResponse getGatewayPayment(String portonePaymentId) {
        return paymentGateway.getPayment(portonePaymentId);
    }

    /**
     * PortOne 결제 단건 조회 결과가 우리 서버가 기대한 결제 정보와 같은지 검증한다.
     *
     * <p>이 메서드는 조회 결과의 유효성만 판단하고, 보상 취소 여부는 결정하지 않는다.
     * 실제 보상 취소 실행 여부는 유스케이스 순서를 조율하는 Facade가 판단한다.
     *
     * <p>반대로 상태가 {@code PAID}인데 금액이 다르거나 승인 시각이 없다면,
     * 외부 결제는 성공했지만 우리 서버가 안전하게 확정할 수 없는 상태다.
     * 이 경우 이 메서드는 예외를 던지고, Facade가 외부 결제 취소를 이어서 수행한다.
     *
     * @param target 내부 DB에서 검증한 결제 확정 대상 정보
     * @param gatewayResponse PortOne 결제 단건 조회 결과
     */
    public void validatePaidPayment(ConfirmPaymentTarget target, PaymentGatewayResponse gatewayResponse) {
        if (gatewayResponse == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }

        if (!gatewayResponse.hasSamePaymentId(target.portonePaymentId())) {
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ID_MISMATCH);
        }

        if (!gatewayResponse.isPaid()) {
            throw new BusinessException(ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }

        if (gatewayResponse.paidAmount() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }

        if (!gatewayResponse.hasSameAmount(target.pgAmount())) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if (gatewayResponse.approvedAt() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }
    }
}
