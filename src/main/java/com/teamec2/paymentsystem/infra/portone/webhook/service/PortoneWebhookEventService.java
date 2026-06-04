package com.teamec2.paymentsystem.infra.portone.webhook.service;

import com.teamec2.paymentsystem.infra.portone.webhook.dto.PortoneWebhookReceiveResponse;
import com.teamec2.paymentsystem.infra.portone.webhook.entity.PortoneWebhookEvent;
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

    private final PortoneWebhookEventRepository webhookEventRepository;

    /**
     * 검증이 끝난 PortOne 웹훅을 수신 이력으로 저장한다.
     *
     * <p>같은 {@code webhookId}가 이미 저장되어 있으면 중복 수신으로 보고 새 이벤트를 만들지 않는다.
     * 현재 수신 기반 단계에서는 결제 확정 처리를 수행하지 않고, 처리 대상 이벤트는 {@code RECEIVED},
     * 지원하지 않는 이벤트는 {@code IGNORE} 상태로만 기록한다.
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
        if (isDuplicatedWebhook(webhookId)) {
            return PortoneWebhookReceiveResponse.duplicated();
        }

        String type = resolveType(webhook);
        String portonePaymentId = resolvePortonePaymentId(webhook);

        if (isUnsupportedEvent(webhook)) {
            return saveIgnoredEvent(webhookId, type, portonePaymentId, rawPayload);
        }

        return saveReceivedEvent(webhookId, type, portonePaymentId, rawPayload);
    }

    /**
     * 이미 저장된 webhookId인지 확인해 PortOne 재전송을 중복으로 판단한다.
     */
    private boolean isDuplicatedWebhook(String webhookId) {
        return webhookEventRepository.existsByWebhookId(webhookId);
    }

    /**
     * 현재 단계에서 처리하지 않는 웹훅 이벤트인지 확인한다.
     */
    private boolean isUnsupportedEvent(Webhook webhook) {
        return !(webhook instanceof WebhookTransactionPaid);
    }

    /**
     * 처리 대상 웹훅을 RECEIVED 상태로 저장하고 수신 응답을 만든다.
     */
    private PortoneWebhookReceiveResponse saveReceivedEvent(
            String webhookId,
            String type,
            String portonePaymentId,
            String rawPayload
    ) {
        PortoneWebhookEvent event = PortoneWebhookEvent.received(
                webhookId,
                type,
                portonePaymentId,
                rawPayload
        );
        if (!saveWebhookEvent(event, webhookId)) {
            return PortoneWebhookReceiveResponse.duplicated();
        }

        return PortoneWebhookReceiveResponse.received(portonePaymentId);
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
            return PortoneWebhookReceiveResponse.duplicated();
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
            if (isDuplicatedWebhook(webhookId)) {
                return false;
            }

            throw e;
        }
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

}
