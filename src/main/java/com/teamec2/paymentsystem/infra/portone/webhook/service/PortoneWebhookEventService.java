package com.teamec2.paymentsystem.infra.portone.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.facade.PaymentFacade;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.refund.service.RefundProcessingTxService;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.infra.portone.webhook.dto.PortoneWebhookReceiveResponse;
import com.teamec2.paymentsystem.infra.portone.webhook.entity.PortoneWebhookEvent;
import com.teamec2.paymentsystem.infra.portone.webhook.entity.WebhookEventStatus;
import com.teamec2.paymentsystem.infra.portone.webhook.repository.PortoneWebhookEventRepository;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookTransactionCancelledCancelPending;
import io.portone.sdk.server.webhook.WebhookTransactionCancelledCancelled;
import io.portone.sdk.server.webhook.WebhookTransactionCancelledPartialCancelled;
import io.portone.sdk.server.webhook.WebhookTransactionFailed;
import io.portone.sdk.server.webhook.WebhookTransactionPaid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PortoneWebhookEventService {

    private static final String TYPE_TRANSACTION_PAID = "Transaction.Paid";
    private static final String TYPE_TRANSACTION_FAILED = "Transaction.Failed";
    private static final String TYPE_TRANSACTION_CANCELLED = "Transaction.Cancelled";
    private static final String TYPE_TRANSACTION_PARTIAL_CANCELLED = "Transaction.PartialCancelled";
    private static final String TYPE_TRANSACTION_CANCEL_PENDING = "Transaction.CancelPending";

    private static final String REASON_UNSUPPORTED_EVENT_TYPE = "UNSUPPORTED_EVENT_TYPE";
    private static final String REASON_CANCELLATION_ID_MISSING = "WEBHOOK_CANCELLATION_ID_MISSING";
    private static final String REASON_WEBHOOK_PAYLOAD_PARSE_FAILED = "WEBHOOK_PAYLOAD_PARSE_FAILED";

    private final PortoneWebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentFacade paymentFacade;
    private final RefundProcessingTxService refundProcessingTxService;

    // Spring Bean으로 등록된 ObjectMapper가 없는 환경에서도 rawPayload JSON 파싱이 가능하도록 직접 생성합니다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    /**
     * 검증이 끝난 PortOne 웹훅을 수신 이력으로 저장하고 처리 대상이면 결제를 확정한다.
     *
     * <p>같은 {@code webhookId}가 이미 저장되어 있으면 저장된 처리 상태를 기준으로 분기한다.
     * 성공/무시 처리가 끝난 이벤트는 멱등 응답을 반환하고, 실패 또는 수신 중단 상태로 남은
     * 결제 완료 이벤트는 다시 확정 처리를 시도한다.
     *
     * <p>결제 확정 중 예외가 발생하면 이벤트를 {@code FAILED} 상태로 저장한 뒤 원래 예외를 다시 던진다.
     *
     * @param webhookId PortOne 웹훅 헤더의 메시지 ID
     * @param webhook PortOne SDK가 검증 후 파싱한 웹훅 객체
     * @param rawPayload 서명 검증에 사용한 원본 요청 본문
     * @return 웹훅 수신 결과
     */
    public PortoneWebhookReceiveResponse receive(
            String webhookId,
            Webhook webhook,
            String rawPayload
    ) {
        return webhookEventRepository.findByWebhookId(webhookId)
                .map(this::handleExistingWebhookEvent)
                .orElseGet(() -> receiveNewWebhook(webhookId, webhook, rawPayload));
    }

    /**
     * 아직 저장되지 않은 웹훅을 새 수신 이력으로 만들고 처리한다.
     */
    private PortoneWebhookReceiveResponse receiveNewWebhook(
            String webhookId,
            Webhook webhook,
            String rawPayload
    ) {
        String type = resolveType(webhook);
        String portonePaymentId = resolvePortonePaymentId(webhook);

        if (isUnsupportedEvent(webhook)) {
            return saveIgnoredEvent(webhookId, type, portonePaymentId, rawPayload);
        }

        validatePortonePaymentId(portonePaymentId);

        PortoneWebhookEvent event = PortoneWebhookEvent.received(
                webhookId,
                type,
                portonePaymentId,
                rawPayload
        );
        if (!saveWebhookEvent(event, webhookId)) {
            return handleConcurrentDuplicateWebhook(webhookId);
        }

        if (webhook instanceof WebhookTransactionPaid) {
            return processPaidEvent(event, portonePaymentId);
        }

        if (isRefundCompletedEvent(webhook)) {
            return processRefundCompletedEvent(event, portonePaymentId, rawPayload);
        }

        return saveIgnoredEvent(webhookId, type, portonePaymentId, rawPayload);
    }

    /**
     * 이미 저장된 웹훅의 상태를 기준으로 멱등 응답 또는 재처리를 수행한다.
     */
    private PortoneWebhookReceiveResponse handleExistingWebhookEvent(PortoneWebhookEvent event) {
        if (isRetryablePaidEvent(event)) {
            return reprocessPaidEvent(event);
        }

        return PortoneWebhookReceiveResponse.duplicated();
    }

    private boolean isRetryablePaidEvent(PortoneWebhookEvent event) {
        WebhookEventStatus status = event.getStatus();

        return TYPE_TRANSACTION_PAID.equals(event.getType())
                && (status == WebhookEventStatus.FAILED || status == WebhookEventStatus.RECEIVED);
    }

    private PortoneWebhookReceiveResponse reprocessPaidEvent(PortoneWebhookEvent event) {
        String portonePaymentId = event.getPortonePaymentId();
        validatePortonePaymentId(portonePaymentId);

        return processPaidEvent(event, portonePaymentId);
    }

    /**
     * 현재 단계에서 처리하지 않는 웹훅 이벤트인지 확인한다.
     */
    private boolean isUnsupportedEvent(Webhook webhook) {
        return !(webhook instanceof WebhookTransactionPaid)
                && !isRefundCompletedEvent(webhook);
    }

    /**
     * 환불 완료로 볼 수 있는 웹훅인지 확인합니다.
     */
    private boolean isRefundCompletedEvent(Webhook webhook) {
        return webhook instanceof WebhookTransactionCancelledCancelled
                || webhook instanceof WebhookTransactionCancelledPartialCancelled;
    }

    /**
     * 결제 완료 웹훅의 내부 결제 확정을 실행하고 웹훅 이벤트 상태를 갱신한다.
     *
     * <p>확정 성공 시 결제 엔티티를 웹훅 이벤트에 연결해 {@code PROCESSED}로 저장한다.
     * 실패 시 실패 사유를 기록한 뒤 예외를 다시 던져 컨트롤러가 실패 응답을 반환하게 한다.
     *
     * @param event 수신 상태로 저장된 웹훅 이벤트
     * @param portonePaymentId 확정할 PortOne 결제 ID
     * @return 결제 확정까지 완료된 웹훅 수신 응답
     */
    private PortoneWebhookReceiveResponse processPaidEvent(
            PortoneWebhookEvent event,
            String portonePaymentId
    ) {
        try {
            ConfirmPaymentResponse confirmPaymentResponse = paymentFacade.confirmPaidWebhook(portonePaymentId);
            Payment payment = paymentRepository.findById(confirmPaymentResponse.paymentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            event.markProcessed(payment);
            webhookEventRepository.saveAndFlush(event);

            return PortoneWebhookReceiveResponse.processed(portonePaymentId);
        } catch (RuntimeException e) {
            event.markFailed(resolveFailureReason(e));
            webhookEventRepository.saveAndFlush(event);
            throw e;
        }
    }

    /**
     * 취소 완료 또는 부분 취소 완료 웹훅을 처리합니다.
     * 처리 흐름:
     * 1. rawPayload에서 PortOne cancellationId를 추출합니다.
     * 2. cancellationId 기준으로 RefundOutbox를 찾아 내부 환불 완료 처리를 수행합니다.
     * 3. 웹훅 이벤트를 PROCESSED 상태로 변경합니다.
     */
    private PortoneWebhookReceiveResponse processRefundCompletedEvent(
            PortoneWebhookEvent event,
            String portonePaymentId,
            String rawPayload
    ) {
        try {
            String portoneCancellationId = resolvePortoneCancellationId(rawPayload);
            validatePortoneCancellationId(portoneCancellationId);

            refundProcessingTxService.completeByPortoneCancellationId(
                    portonePaymentId,
                    portoneCancellationId
            );

            Payment payment = paymentRepository.findByPortonePaymentId(portonePaymentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

            event.markProcessed(payment);
            webhookEventRepository.saveAndFlush(event);

            return PortoneWebhookReceiveResponse.processed(portonePaymentId);
        } catch (RuntimeException e) {
            event.markFailed(resolveFailureReason(e));
            webhookEventRepository.saveAndFlush(event);
            throw e;
        }
    }

    /**
     * 처리하지 않는 웹훅을 IGNORE 상태로 저장하고 무시 응답을 만든다.
     */
    private PortoneWebhookReceiveResponse saveIgnoredEvent(
            String webhookId,
            String type,
            String portonePaymentId,
            String rawPayload
    ) {
        PortoneWebhookEvent event = PortoneWebhookEvent.ignored(
                webhookId,
                type,
                portonePaymentId,
                rawPayload,
                REASON_UNSUPPORTED_EVENT_TYPE
        );
        if (!saveWebhookEvent(event, webhookId)) {
            return handleConcurrentDuplicateWebhook(webhookId);
        }

        return PortoneWebhookReceiveResponse.ignored(REASON_UNSUPPORTED_EVENT_TYPE);
    }

    /**
     * 웹훅 이벤트를 저장하고, 동시 중복 수신으로 인한 unique 충돌만 중복으로 처리한다.
     */
    private boolean saveWebhookEvent(PortoneWebhookEvent event, String webhookId) {
        try {
            webhookEventRepository.saveAndFlush(event);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (webhookEventRepository.existsByWebhookId(webhookId)) {
                return false;
            }

            throw e;
        }
    }

    /**
     * 저장 직전 다른 요청이 같은 webhookId를 먼저 저장한 경우 기존 이력을 다시 읽어 처리한다.
     */
    private PortoneWebhookReceiveResponse handleConcurrentDuplicateWebhook(String webhookId) {
        return webhookEventRepository.findByWebhookId(webhookId)
                .map(this::handleExistingWebhookEvent)
                .orElseGet(PortoneWebhookReceiveResponse::duplicated);
    }

    /**
     * 저장할 웹훅 이벤트 타입 문자열을 구한다.
     */
    private String resolveType(Webhook webhook) {
        if (webhook instanceof WebhookTransactionPaid) {
            return TYPE_TRANSACTION_PAID;
        }

        if (webhook instanceof WebhookTransactionFailed) {
            return TYPE_TRANSACTION_FAILED;
        }

        if (webhook instanceof WebhookTransactionCancelledCancelled) {
            return TYPE_TRANSACTION_CANCELLED;
        }

        if (webhook instanceof WebhookTransactionCancelledPartialCancelled) {
            return TYPE_TRANSACTION_PARTIAL_CANCELLED;
        }

        if (webhook instanceof WebhookTransactionCancelledCancelPending) {
            return TYPE_TRANSACTION_CANCEL_PENDING;
        }

        return webhook.getClass().getSimpleName();
    }

    /**
     * 결제 관련 웹훅이면 PortOne 결제 ID를 추출한다.
     */
    private String resolvePortonePaymentId(Webhook webhook) {
        if (webhook instanceof WebhookTransaction transaction) {
            return transaction.getData().getPaymentId();
        }

        return null;
    }

    /**
     * rawPayload에서 PortOne cancellationId를 추출합니다.
     *
     * PortOne SDK 웹훅 객체에서 cancellationId를 바로 꺼내는 메서드명이 프로젝트 SDK 버전에 따라
     * 다를 수 있기 때문에, 현재는 서명 검증에 사용한 원본 payload에서 JSON 경로로 추출합니다.
     */
    private String resolvePortoneCancellationId(String rawPayload) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawPayload);

            return firstTextValue(
                    root.at("/data/cancellationId"),
                    root.at("/data/cancellation/id"),
                    root.at("/data/cancelId"),
                    root.at("/cancellationId"),
                    root.at("/cancellation/id")
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(REASON_WEBHOOK_PAYLOAD_PARSE_FAILED, e);
        }
    }

    private String firstTextValue(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node == null || node.isMissingNode() || node.isNull()) {
                continue;
            }

            String value = node.asText();

            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    /**
     * 처리 대상 웹훅에 PortOne 결제 ID가 포함되어 있는지 확인한다.
     *
     * @param portonePaymentId 웹훅에서 추출한 PortOne 결제 ID
     */
    private void validatePortonePaymentId(String portonePaymentId) {
        if (portonePaymentId == null || portonePaymentId.isBlank()) {
            throw new BusinessException(ErrorCode.WEBHOOK_PAYMENT_ID_MISSING);
        }
    }

    /**
     * 처리 대상 취소 웹훅에 PortOne 취소 ID가 포함되어 있는지 확인합니다.
     */
    private void validatePortoneCancellationId(String portoneCancellationId) {
        if (portoneCancellationId == null || portoneCancellationId.isBlank()) {
            throw new IllegalArgumentException(REASON_CANCELLATION_ID_MISSING);
        }
    }

    /**
     * 웹훅 처리 실패 이력에 저장할 실패 사유를 결정한다.
     *
     * <p>도메인 예외는 운영자가 원인을 식별하기 쉽도록 {@link ErrorCode} 이름으로 저장하고,
     * 그 외 런타임 예외는 예외 클래스명을 저장한다.
     *
     * @param exception 웹훅 처리 중 발생한 예외
     * @return 실패 이력에 남길 사유 문자열
     */
    private String resolveFailureReason(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode().name();
        }

        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }

        return exception.getClass().getSimpleName();
    }
}

