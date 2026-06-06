package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 결제 확정 흐름 전체를 지휘하는 Facade 서비스.
 *
 * <p>이 클래스는 직접 DB 상태를 변경하지 않고, 외부 PortOne API 호출과 내부 트랜잭션 서비스를
 * 순서대로 호출한다. 외부 API 호출을 DB 트랜잭션 안에 오래 묶지 않기 위해
 * {@link PaymentConfirmTxService}에 실제 DB 변경 작업을 위임한다.
 *
 * <p>주요 흐름은 다음과 같다.
 * <ol>
 *     <li>내부 DB에서 결제 확정 대상 주문/결제를 조회하고 검증한다.</li>
 *     <li>PG 결제가 필요한 경우 PortOne 결제 단건 조회로 실제 결제 성공 여부를 확인한다.</li>
 *     <li>내부 주문/결제 완료 트랜잭션을 실행한다.</li>
 *     <li>외부 결제는 성공했지만 내부 완료 처리에 실패하면 PortOne 결제 취소로 보상한다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String COMPENSATION_REASON = "PAYMENT_CONFIRM_INTERNAL_FAILURE";
    private static final String COMPENSATION_IDEMPOTENCY_KEY_PREFIX = "payment-confirm-compensation-";

    private final PaymentConfirmTxService paymentConfirmTxService;
    private final PaymentGateway paymentGateway;

    /**
     * PortOne 결제 조회 결과를 검증한 뒤 주문과 결제를 완료 상태로 변경한다.
     *
     * <p>이미 완료된 결제는 상태를 다시 변경하지 않고 현재 확정 결과를 반환한다.
     * <p>PG 결제 금액이 없는 포인트 전액 결제는 외부 결제 조회 없이 확정한다.
     * <p>PG 결제가 이미 성공한 뒤 내부 완료 처리에서 예외가 발생하면
     * PortOne 취소 API를 호출해 외부 결제를 되돌린다.
     *
     * @param userId 결제 확정을 요청한 사용자 ID
     * @param request 결제 확정 요청 정보
     * @return 결제 확정 결과
     */
    public ConfirmPaymentResponse confirmPayment(Long userId, ConfirmPaymentRequest request) {
        ConfirmPaymentTarget target = paymentConfirmTxService.prepare(userId, request);

        if (target.alreadyCompleted()) {
            return target.completedResponse();
        }

        if (target.pointOnly()) {
            return paymentConfirmTxService.complete(target.paymentId(), LocalDateTime.now());
        }

        PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(target.portonePaymentId());
        validateGatewayPayment(target, gatewayResponse);

        try {
            return paymentConfirmTxService.complete(target.paymentId(), gatewayResponse.approvedAt());
        } catch (RuntimeException e) {
            compensateExternalSuccess(target, gatewayResponse.paidAmount());
            throw e;
        }
    }

    /**
     * PortOne 결제 단건 조회 결과가 우리 서버가 기대한 결제 정보와 같은지 검증한다.
     *
     * <p>PortOne 응답 상태가 {@code PAID}가 아니면 아직 외부 결제가 성공한 것이 아니므로
     * 보상 취소를 호출하지 않는다.
     *
     * <p>반대로 상태가 {@code PAID}인데 금액이 다르거나 승인 시각이 없다면,
     * 외부 결제는 성공했지만 우리 서버가 안전하게 확정할 수 없는 상태다.
     * 이 경우에는 클라이언트에게 오류를 반환하기 전에 PortOne 결제를 취소한다.
     *
     * @param target 내부 DB에서 검증한 결제 확정 대상 정보
     * @param gatewayResponse PortOne 결제 단건 조회 결과
     */
    private void validateGatewayPayment(ConfirmPaymentTarget target, PaymentGatewayResponse gatewayResponse) {
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
            BusinessException reason = new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
            compensateExternalSuccess(target, gatewayResponse.paidAmount());
            throw reason;
        }

        if (gatewayResponse.approvedAt() == null) {
            BusinessException reason = new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
            compensateExternalSuccess(target, gatewayResponse.paidAmount());
            throw reason;
        }
    }

    /**
     * 외부 결제 성공 후 내부 처리 실패가 발생했을 때 PortOne 결제를 취소한다.
     *
     * <p>이 메서드는 일반적인 사용자 환불 기능이 아니다. 결제 확정 과정에서
     * "PortOne은 성공했지만 우리 DB 완료 처리가 실패한 상황"을 되돌리기 위한 보상 작업이다.
     *
     * <p>PortOne 취소가 성공하면 내부 주문/결제도 실패 상태로 정리한다.
     * PortOne 취소 자체가 실패하면 내부 상태를 완료/실패로 섣불리 바꾸지 않고
     * {@link ErrorCode#PAYMENT_COMPENSATION_FAILED}를 반환해 운영자가 확인할 수 있게 한다.
     *
     * @param target 보상 취소할 내부 결제 정보
     * @param cancelAmount PortOne에서 실제 성공 처리된 금액
     */
    private void compensateExternalSuccess(
            ConfirmPaymentTarget target,
            Long cancelAmount
    ) {
        try {
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

            paymentConfirmTxService.failAfterCompensation(target.paymentId());
        } catch (RuntimeException compensationFailure) {
            log.error(
                    "결제 확정 보상 취소 실패: paymentId={}, portonePaymentId={}",
                    target.paymentId(),
                    target.portonePaymentId(),
                    compensationFailure
            );
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_FAILED);
        }
    }
}
