package com.teamec2.paymentsystem.domain.payment.facade;

import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.service.ConfirmPaymentTarget;
import com.teamec2.paymentsystem.domain.payment.service.PaymentCompensationProcessor;
import com.teamec2.paymentsystem.domain.payment.service.PaymentConfirmTxService;
import com.teamec2.paymentsystem.domain.payment.service.PaymentService;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 결제 확정 유스케이스의 순서를 조율하는 Facade.
 *
 * <p>외부 PG 조회는 {@link PaymentService}, DB 상태 변경은 {@link PaymentConfirmTxService}에 맡긴다.
 * 이 클래스는 두 서비스를 어떤 순서로 호출할지만 담당한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFacade {

    private final PaymentConfirmTxService paymentConfirmTxService;
    private final PaymentService paymentService;
    private final PaymentCompensationProcessor paymentCompensationProcessor;

    /**
     * 인증 사용자의 결제 확정 요청을 처리한다.
     *
     * @param userId 결제 확정을 요청한 사용자 ID
     * @param request 결제 확정 요청 정보
     * @return 결제 확정 결과
     */
    public ConfirmPaymentResponse confirmPayment(Long userId, ConfirmPaymentRequest request) {
        ConfirmPaymentTarget target = paymentConfirmTxService.prepare(userId, request);
        return confirmPreparedPayment(target);
    }

    /**
     * PortOne 결제 완료 웹훅을 기준으로 결제를 확정한다.
     *
     * @param portonePaymentId PortOne 결제 ID
     * @return 결제 확정 결과
     */
    public ConfirmPaymentResponse confirmPaidWebhook(String portonePaymentId) {
        ConfirmPaymentTarget target = paymentConfirmTxService.prepareByPortonePaymentId(portonePaymentId);
        return confirmPreparedPayment(target);
    }

    private ConfirmPaymentResponse confirmPreparedPayment(ConfirmPaymentTarget target) {
        if (target.alreadyCompleted()) {
            return target.completedResponse();
        }

        if (target.pointOnly()) {
            return paymentConfirmTxService.complete(target.paymentId(), LocalDateTime.now());
        }

        PaymentGatewayResponse gatewayResponse = paymentService.getGatewayPayment(target.portonePaymentId());

        try {
            paymentService.validatePaidPayment(target, gatewayResponse);
        } catch (RuntimeException e) {
            compensateValidationFailureIfNeeded(target, gatewayResponse, e);
            throw e;
        }

        try {
            return paymentConfirmTxService.complete(target.paymentId(), gatewayResponse.approvedAt());
        } catch (RuntimeException e) {
            recoverCompleteFailure(target, gatewayResponse, e);
            throw e;
        }
    }

    private void compensateValidationFailureIfNeeded(
            ConfirmPaymentTarget target,
            PaymentGatewayResponse gatewayResponse,
            RuntimeException validationFailure
    ) {
        if (!shouldCompensateValidationFailure(validationFailure)) {
            return;
        }

        registerAndProcessCompensation(target, gatewayResponse, validationFailure);
    }

    private void recoverCompleteFailure(
            ConfirmPaymentTarget target,
            PaymentGatewayResponse gatewayResponse,
            RuntimeException completeFailure
    ) {
        if (shouldCompensateCompleteFailure(completeFailure)) {
            registerAndProcessCompensation(target, gatewayResponse, completeFailure);
            return;
        }

        paymentConfirmTxService.markConfirmRetryRequired(
                target.paymentId(),
                gatewayResponse.approvedAt(),
                completeFailure.getMessage()
        );
    }

    private void registerAndProcessCompensation(
            ConfirmPaymentTarget target,
            PaymentGatewayResponse gatewayResponse,
            RuntimeException cause
    ) {
        if (!isCompensatableExternalSuccess(target, gatewayResponse)) {
            return;
        }

        try {
            Long outboxId = paymentConfirmTxService.markCompensationRequired(
                    target.paymentId(),
                    gatewayResponse.paidAmount(),
                    cause.getMessage()
            );

            if (outboxId != null) {
                paymentCompensationProcessor.process(outboxId);
            }
        } catch (RuntimeException compensationFailure) {
            log.error(
                    "결제 확정 보상 처리 등록 실패: paymentId={}, portonePaymentId={}",
                    target.paymentId(),
                    target.portonePaymentId(),
                    compensationFailure
            );
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_FAILED);
        }
    }

    private boolean isCompensatableExternalSuccess(
            ConfirmPaymentTarget target,
            PaymentGatewayResponse gatewayResponse
    ) {
        return gatewayResponse != null
                && gatewayResponse.hasSamePaymentId(target.portonePaymentId())
                && gatewayResponse.isPaid()
                && gatewayResponse.paidAmount() != null;
    }

    private boolean shouldCompensateValidationFailure(RuntimeException e) {
        if (!(e instanceof BusinessException businessException)) {
            return false;
        }

        ErrorCode errorCode = businessException.getErrorCode();

        return errorCode == ErrorCode.PAYMENT_AMOUNT_MISMATCH
                || errorCode == ErrorCode.EXTERNAL_API_FAILED;
    }

    private boolean shouldCompensateCompleteFailure(RuntimeException e) {
        if (!(e instanceof BusinessException businessException)) {
            return false;
        }

        ErrorCode errorCode = businessException.getErrorCode();

        return errorCode == ErrorCode.INVALID_ORDER_STATUS
                || errorCode == ErrorCode.ORDER_CANCEL_NOT_ALLOWED
                || errorCode == ErrorCode.CONFLICT;
    }
}
