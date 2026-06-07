package com.teamec2.paymentsystem.infra.portone.client;

import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentCancelStatus;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.infra.portone.config.PortoneProperties;
import com.teamec2.paymentsystem.infra.portone.dto.PortoneCancelPaymentRequest;
import com.teamec2.paymentsystem.infra.portone.dto.PortoneCancelPaymentResponse;
import com.teamec2.paymentsystem.infra.portone.dto.PortonePaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.ZoneId;
import java.util.Locale;

/**
 * PortOne REST API와 통신하는 결제 게이트웨이 구현체.
 *
 * <p>도메인 계층은 {@link PaymentGateway} 인터페이스만 의존하고,
 * 실제 HTTP 요청 경로, 인증 헤더, PortOne 요청/응답 DTO는 이 클래스에서 처리한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PortoneClient implements PaymentGateway {

    private final RestClient portoneRestClient;
    private final PortoneProperties portoneProperties;

    /**
     * PortOne 결제 단건 조회 API를 호출한다.
     *
     * <p>결제 확정 시 클라이언트가 보낸 결제 성공 정보나 웹훅 본문을 그대로 신뢰하지 않고,
     * 이 API 응답으로 실제 상태와 금액을 다시 검증한다.
     *
     * @param paymentId PortOne 결제 ID
     * @return 도메인 계층에서 사용하는 결제 조회 응답
     */
    @Override
    public PaymentGatewayResponse getPayment(String paymentId) {
        log.info("PortOne 결제 조회: {}", paymentId);

        try {
            PortonePaymentResponse response = portoneRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/payments/{paymentId}")
                            .queryParam("storeId", portoneProperties.storeId())
                            .build(paymentId))
                    .retrieve()
                    .body(PortonePaymentResponse.class);

            validateResponse(response);

            return new PaymentGatewayResponse(
                    response.id(),
                    response.status(),
                    response.amount().total(),
                    response.paidAt() == null ? null : response.paidAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDateTime()
            );
        } catch (RestClientException e) {
            log.warn("PortOne 결제 조회 실패: paymentId={}", paymentId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }
    }

    /**
     * PortOne 결제 취소 API를 호출합니다.
     *
     * 사용 목적:
     * 1. 결제 확정 보상 취소
     * 2. 사용자 환불 처리
     *
     * Idempotency-Key는 같은 취소 요청이 네트워크 재시도 등으로 중복 전송되더라도
     * PortOne에서 같은 요청으로 인식할 수 있게 하기 위한 값입니다.
     *
     * @param paymentId PortOne 결제 ID
     * @param cancelAmount 이번에 취소할 금액
     * @param currentCancellableAmount 현재 취소 가능 금액
     * @param reason 취소 사유
     * @param idempotencyKey PortOne 취소 요청 멱등 키
     * @return 도메인 계층에서 사용하는 결제 취소 응답
     */
    @Override
    public PaymentCancelResponse cancelPayment(
            String paymentId,
            Long cancelAmount,
            Long currentCancellableAmount,
            String reason,
            String idempotencyKey
    ) {
        validateCancelRequest(
                paymentId,
                cancelAmount,
                currentCancellableAmount,
                reason,
                idempotencyKey
        );

        log.info(
                "PortOne 결제 취소 요청. paymentId={}, cancelAmount={}, currentCancellableAmount={}",
                paymentId,
                cancelAmount,
                currentCancellableAmount
        );

        try {
            PortoneCancelPaymentResponse response = portoneRestClient.post()
                    .uri("/payments/{paymentId}/cancel", paymentId)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(new PortoneCancelPaymentRequest(
                            portoneProperties.storeId(),
                            cancelAmount,
                            reason,
                            "ADMIN",
                            // 보상 취소는 현재 승인된 PG 금액 전체를 되돌리는 용도라 취소 가능 금액도 같은 값으로 검증한다.
                            currentCancellableAmount
                    ))
                    .retrieve()
                    .body(PortoneCancelPaymentResponse.class);

            validateCancelResponse(response);

            String rawStatus = response.cancellation().status();

            return new PaymentCancelResponse(
                    response.cancellation().id(),
                    rawStatus,
                    mapCancelStatus(rawStatus)
            );
        } catch (RestClientException e) {
            log.warn(
                    "PortOne 결제 취소 요청 실패. paymentId={}, cancelAmount={}, currentCancellableAmount={}",
                    paymentId,
                    cancelAmount,
                    currentCancellableAmount,
                    e
            );

            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_REQUEST_FAILED);
        }
    }

    /**
     * PortOne 결제 조회 응답이 결제 검증에 필요한 최소 필드를 포함하는지 확인한다.
     *
     * <p>응답 형식이 예상과 다르면 외부 API 호출 실패로 보고 결제 확정을 진행하지 않는다.
     *
     * @param response PortOne 결제 조회 응답
     */
    private void validateResponse(PortonePaymentResponse response) {
        if (response == null
                || response.id() == null
                || response.status() == null
                || response.amount() == null
                || response.amount().total() == null) {
            log.warn("PortOne 결제 조회 응답 형식 오류");
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }
    }

    /**
     * PortOne 결제 취소 응답이 보상 처리에 필요한 최소 필드를 포함하는지 확인한다.
     *
     * <p>취소 ID나 상태가 없으면 실제 취소 성공 여부를 판단할 수 없으므로
     * 보상 취소 실패로 처리한다.
     *
     * cancelPayment()는 보상 취소와 환불 취소 모두에서 쓰이므로, 외부 결제 취소 요청 실패 에러 코드로 사용합니다.
     *
     * @param response PortOne 결제 취소 응답
     */
    private void validateCancelResponse(PortoneCancelPaymentResponse response) {
        if (response == null
                || response.cancellation() == null
                || response.cancellation().id() == null
                || response.cancellation().status() == null) {
            log.warn("PortOne 결제 취소 응답 형식 오류");
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_REQUEST_FAILED);
        }
    }

    /**
     * PortOne 결제 취소 요청의 필수값을 검증합니다.
     */
    private void validateCancelRequest(
            String paymentId,
            Long cancelAmount,
            Long currentCancellableAmount,
            String reason,
            String idempotencyKey
    ) {
        if (paymentId == null || paymentId.isBlank()
                || cancelAmount == null
                || currentCancellableAmount == null
                || idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        if (cancelAmount <= 0 || currentCancellableAmount < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (cancelAmount > currentCancellableAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }

        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }
    }

    /**
     * PortOne 원본 상태값(rawStatus) 해석은 infra 계층인 PortoneClient에서 담당합니다.
     * RefundProcessor는 PortOne의 REQUESTED, FAILED 같은 문자열을 직접 알 필요가 없습니다.
     */
    private PaymentCancelStatus mapCancelStatus(String rawStatus) {
        String status = normalizeStatus(rawStatus);

        if ("SUCCEEDED".equals(status)) {
            return PaymentCancelStatus.SUCCEEDED;
        }

        if ("FAILED".equals(status)) {
            return PaymentCancelStatus.FAILED;
        }

        /*
         * REQUESTED는 취소 요청이 접수되었지만 최종 성공/실패가 확정되지 않은 상태입니다.
         *
         * 그 외 알 수 없는 상태도 환불 도메인에서는 실패로 단정하지 않고
         * RESULT_UNKNOWN으로 처리하는 것이 안전합니다.
         */
        return PaymentCancelStatus.RESULT_UNKNOWN;
    }

    /**
     * 외부에서 받은 상태 문자열을 정리하는 메서드입니다.
     */
    private String normalizeStatus(String status) {
        if (status == null) {
            return "";
        }

        return status.trim().toUpperCase(Locale.ROOT);
    }
}

