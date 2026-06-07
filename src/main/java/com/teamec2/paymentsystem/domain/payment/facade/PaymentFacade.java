package com.teamec2.paymentsystem.domain.payment.facade;

import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.service.PaymentService;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 결제 확정 유스케이스의 전체 흐름을 조율하는 퍼사드.
 *
 * <p>이 클래스는 외부 PortOne API 호출과 내부 DB 트랜잭션 작업의 순서를 결정한다.
 * 직접 repository를 참조하거나 DB 상태를 변경하지 않고, 내부 상태 조회/변경은
 * {@link PaymentService}에 위임한다. 이렇게 외부 API 호출을 DB 트랜잭션 밖에서 실행해
 * 트랜잭션 점유 시간을 줄이고, 내부 완료 실패 시 보상 취소를 명확하게 수행한다.
 *
 * <p>주요 흐름은 다음과 같다.
 * <ol>
 *     <li>내부 DB 기준으로 결제 확정 대상을 준비한다.</li>
 *     <li>이미 완료된 결제면 멱등 응답을 반환한다.</li>
 *     <li>포인트 전액 결제면 외부 API 조회 없이 내부 완료 처리만 실행한다.</li>
 *     <li>PG 결제면 PortOne 결제 단건 조회 결과를 검증한다.</li>
 *     <li>내부 완료 처리 실패 시 PortOne 결제를 보상 취소한다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentFacade {

    private static final String COMPENSATION_REASON = "PAYMENT_CONFIRM_INTERNAL_FAILURE";
    private static final String COMPENSATION_IDEMPOTENCY_KEY_PREFIX = "payment-confirm-compensation-";

    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    /**
     * 인증 사용자의 결제 확정 요청을 처리한다.
     *
     * <p>사용자 ID와 주문 ID를 기준으로 내부 결제 대상을 준비한 뒤,
     * 포인트 전액 결제와 PG 결제 흐름을 구분해 확정한다.
     *
     * @param userId 결제 확정을 요청한 사용자 ID
     * @param request 결제 확정 요청 정보
     * @return 결제 확정 결과
     */
    public ConfirmPaymentResponse confirmPayment(Long userId, ConfirmPaymentRequest request) {
        ConfirmPaymentTarget target = paymentService.prepare(userId, request);

        return confirmPreparedTarget(target);
    }

    /**
     * PortOne 결제 완료 웹훅을 기준으로 결제를 확정한다.
     *
     * <p>웹훅에는 내부 주문 ID나 사용자 ID가 없으므로 PortOne 결제 ID로
     * 내부 결제를 찾고, 클라이언트 확정 요청과 같은 검증/완료/보상 로직을 사용한다.
     *
     * @param portonePaymentId PortOne 결제 ID
     * @return 결제 확정 결과
     */
    public ConfirmPaymentResponse confirmPaidWebhook(String portonePaymentId) {
        ConfirmPaymentTarget target = paymentService.prepareByPortonePaymentId(portonePaymentId);

        return confirmPreparedTarget(target);
    }

    /**
     * 내부 DB 검증이 끝난 결제 대상을 실제 확정한다.
     *
     * <p>메인 흐름을 읽기 쉽게 유지하기 위해 멱등 응답, 포인트 전액 결제,
     * PG 결제 확정 흐름을 이 메서드에서만 분기한다.
     *
     * @param target 내부 DB 기준으로 준비된 결제 확정 대상
     * @return 결제 확정 결과
     */
    private ConfirmPaymentResponse confirmPreparedTarget(ConfirmPaymentTarget target) {
        if (target.alreadyCompleted()) {
            return target.completedResponse();
        }

        if (target.requiresCompensationCleanup()) {
            completeInternalCompensationCleanup(target);
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        if (target.hasCompensationResultUnknown()) {
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_RESULT_UNKNOWN);
        }

        if (target.pointOnly()) {
            return paymentService.complete(target.paymentId(), LocalDateTime.now());
        }

        PaymentGatewayResponse gatewayResponse = getValidatedGatewayPayment(target);

        return completePgPayment(target, gatewayResponse);
    }

    /**
     * PortOne 결제 단건 조회를 실행하고 응답이 내부 결제 정보와 일치하는지 검증한다.
     *
     * @param target 내부 DB 기준으로 준비된 결제 확정 대상
     * @return 검증이 끝난 PortOne 결제 조회 응답
     */
    private PaymentGatewayResponse getValidatedGatewayPayment(ConfirmPaymentTarget target) {
        PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(target.portonePaymentId());
        validateGatewayPayment(target, gatewayResponse);

        return gatewayResponse;
    }

    /**
     * PortOne 검증이 끝난 PG 결제를 내부 완료 처리한다.
     *
     * <p>외부 결제는 성공했지만 내부 완료 처리에서 예외가 발생하면
     * PortOne 취소 API를 호출해 외부 결제를 되돌린 뒤 원래 예외를 다시 던진다.
     *
     * @param target 내부 DB 기준으로 준비된 결제 확정 대상
     * @param gatewayResponse 검증이 끝난 PortOne 결제 조회 응답
     * @return 결제 확정 결과
     */
    private ConfirmPaymentResponse completePgPayment(
            ConfirmPaymentTarget target,
            PaymentGatewayResponse gatewayResponse
    ) {
        try {
            return paymentService.complete(target.paymentId(), gatewayResponse.approvedAt());
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
     * <p>PortOne 취소가 성공하면 먼저 내부 결제를 보상 정리 필요 상태로 표시한 뒤
     * 내부 주문/결제를 실패 상태로 정리한다. 이 표시가 남아 있으면 내부 정리 실패 후
     * 재시도할 때 PortOne 재취소 없이 DB 정리만 다시 실행할 수 있다.
     *
     * <p>PortOne 취소 결과가 미확정이면 내부 결제를 결과미확정 상태로 표시해 운영자 확인이나
     * 취소 완료 웹훅 처리 대상임을 남긴다. PortOne 취소 자체가 명확히 실패한 경우에만
     * {@link ErrorCode#PAYMENT_COMPENSATION_FAILED}를 반환한다.
     *
     * @param target 보상 취소할 내부 결제 정보
     * @param cancelAmount PortOne에서 실제 성공 처리된 금액
     */
    private void compensateExternalSuccess(
            ConfirmPaymentTarget target,
            Long cancelAmount
    ) {
        PaymentCancelResponse cancelResponse = cancelExternalPayment(target, cancelAmount);

        if (cancelResponse == null) {
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_FAILED);
        }

        if (cancelResponse.isResultUnknown()) {
            markCompensationResultUnknown(target);
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_RESULT_UNKNOWN);
        }

        if (!cancelResponse.isSucceeded()) {
            log.error(
                    "결제 확정 보상 취소 실패: paymentId={}, portonePaymentId={}, cancelStatus={}",
                    target.paymentId(),
                    target.portonePaymentId(),
                    cancelResponse.cancelStatus()
            );
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_FAILED);
        }

        markCompensationRequired(target);
        completeInternalCompensationCleanup(target);
    }

    private PaymentCancelResponse cancelExternalPayment(
            ConfirmPaymentTarget target,
            Long cancelAmount
    ) {
        try {
            return paymentGateway.cancelPayment(
                    target.portonePaymentId(),
                    cancelAmount,
                    cancelAmount,
                    COMPENSATION_REASON,
                    COMPENSATION_IDEMPOTENCY_KEY_PREFIX + target.paymentId()
            );
        } catch (RuntimeException compensationFailure) {
            log.error(
                    "결제 확정 보상 취소 요청 실패: paymentId={}, portonePaymentId={}",
                    target.paymentId(),
                    target.portonePaymentId(),
                    compensationFailure
            );
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_FAILED);
        }
    }

    private void markCompensationRequired(ConfirmPaymentTarget target) {
        try {
            paymentService.markCompensationRequired(target.paymentId());
        } catch (RuntimeException cleanupFailure) {
            log.error(
                    "결제 확정 보상 취소 성공 후 내부 정리 필요 상태 저장 실패: paymentId={}, portonePaymentId={}",
                    target.paymentId(),
                    target.portonePaymentId(),
                    cleanupFailure
            );
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_CLEANUP_FAILED);
        }
    }

    private void markCompensationResultUnknown(ConfirmPaymentTarget target) {
        try {
            paymentService.markCompensationResultUnknown(target.paymentId());
        } catch (RuntimeException cleanupFailure) {
            log.error(
                    "결제 확정 보상 취소 결과미확정 상태 저장 실패: paymentId={}, portonePaymentId={}",
                    target.paymentId(),
                    target.portonePaymentId(),
                    cleanupFailure
            );
        }
    }

    private void completeInternalCompensationCleanup(ConfirmPaymentTarget target) {
        try {
            paymentService.failAfterCompensation(target.paymentId());
        } catch (RuntimeException cleanupFailure) {
            log.error(
                    "결제 확정 보상 취소 성공 후 내부 실패 정리 실패: paymentId={}, portonePaymentId={}",
                    target.paymentId(),
                    target.portonePaymentId(),
                    cleanupFailure
            );
            throw new BusinessException(ErrorCode.PAYMENT_COMPENSATION_CLEANUP_FAILED);
        }
    }
}
