package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 도메인의 PG 검증과 보상 취소를 담당하는 서비스.
 *
 * <p>유스케이스 순서는 Facade가 조율하고, 이 클래스는 PortOne 같은 외부 결제 시스템과의
 * 도메인 규칙만 처리한다. DB 상태 변경은 {@link PaymentConfirmTxService}에 위임한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final String COMPENSATION_REASON = "PAYMENT_CONFIRM_INTERNAL_FAILURE";
    private static final String COMPENSATION_IDEMPOTENCY_KEY_PREFIX = "payment-confirm-compensation-";

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

    /**
     * 외부 결제 성공 후 내부 확정이 불가능할 때 PortOne 결제를 취소한다.
     *
     * <p>이 메서드는 일반적인 사용자 환불 기능이 아니다. 결제 확정 과정에서
     * "PortOne은 성공했지만 우리 DB 완료 처리가 실패한 상황"을 되돌리기 위한 보상 작업이다.
     *
     * <p>이 메서드는 외부 결제 취소 요청만 수행한다. 취소 성공 후 내부 주문/결제를
     * 실패 상태로 정리하는 일은 유스케이스 순서를 조율하는 Facade가 담당한다.
     *
     * @param target 보상 취소할 내부 결제 정보
     * @param cancelAmount PortOne에서 실제 성공 처리된 금액
     */
    public void cancelForConfirmCompensation(
            ConfirmPaymentTarget target,
            Long cancelAmount
    ) {
        PaymentCancelResponse cancelResponse = paymentGateway.cancelPayment(
                target.portonePaymentId(),
                cancelAmount,
                cancelAmount,
                COMPENSATION_REASON,
                COMPENSATION_IDEMPOTENCY_KEY_PREFIX + target.paymentId()
        );

        if (!cancelResponse.isSucceeded()) {
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_FAILED);
        }
    }
}
