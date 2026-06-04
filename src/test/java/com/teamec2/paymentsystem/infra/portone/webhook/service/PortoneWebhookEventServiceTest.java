package com.teamec2.paymentsystem.infra.portone.webhook.service;

import com.teamec2.paymentsystem.infra.portone.webhook.dto.PortoneWebhookReceiveResponse;
import com.teamec2.paymentsystem.infra.portone.webhook.entity.PortoneWebhookEvent;
import com.teamec2.paymentsystem.infra.portone.webhook.entity.WebhookEventStatus;
import com.teamec2.paymentsystem.infra.portone.webhook.repository.PortoneWebhookEventRepository;
import io.portone.sdk.server.webhook.Webhook;
import io.portone.sdk.server.webhook.WebhookTransactionDataPaid;
import io.portone.sdk.server.webhook.WebhookTransactionPaid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortoneWebhookEventServiceTest {

    private static final String WEBHOOK_ID = "webhook-1";
    private static final String RAW_PAYLOAD = """
            {
              "type": "Transaction.Paid",
              "data": {
                "paymentId": "pay_123"
              }
            }
            """;

    @Mock
    PortoneWebhookEventRepository webhookEventRepository;

    @InjectMocks
    PortoneWebhookEventService portoneWebhookEventService;

    @Test
    void 웹훅수신_TransactionPaid이면_RECEIVED상태로저장한다() {
        // given
        when(webhookEventRepository.existsByWebhookId(WEBHOOK_ID)).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any(PortoneWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                WEBHOOK_ID,
                paidWebhook("pay_123"),
                RAW_PAYLOAD
        );

        // then
        ArgumentCaptor<PortoneWebhookEvent> eventCaptor = ArgumentCaptor.forClass(PortoneWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(eventCaptor.capture());
        PortoneWebhookEvent savedEvent = eventCaptor.getValue();

        assertThat(response.received()).isTrue();
        assertThat(response.processed()).isFalse();
        assertThat(response.portonePaymentId()).isEqualTo("pay_123");
        assertThat(response.reason()).isEqualTo("RECEIVED");

        assertThat(savedEvent.getWebhookId()).isEqualTo(WEBHOOK_ID);
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookEventStatus.RECEIVED);
        assertThat(savedEvent.getType()).isEqualTo("Transaction.Paid");
        assertThat(savedEvent.getPortonePaymentId()).isEqualTo("pay_123");
        assertThat(savedEvent.getRawPayload()).isEqualTo(RAW_PAYLOAD);
        assertThat(savedEvent.getFailureReason()).isNull();
        assertThat(savedEvent.getProcessedAt()).isNull();
    }

    @Test
    void 웹훅수신_이미저장된WebhookId이면_중복응답하고저장하지않는다() {
        // given
        when(webhookEventRepository.existsByWebhookId(WEBHOOK_ID)).thenReturn(true);

        // when
        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                WEBHOOK_ID,
                paidWebhook("pay_123"),
                RAW_PAYLOAD
        );

        // then
        assertThat(response.received()).isTrue();
        assertThat(response.processed()).isFalse();
        assertThat(response.portonePaymentId()).isNull();
        assertThat(response.reason()).isEqualTo("DUPLICATE_WEBHOOK_ID");

        verify(webhookEventRepository, never()).saveAndFlush(any(PortoneWebhookEvent.class));
    }

    @Test
    void 웹훅수신_지원하지않는이벤트이면_IGNORE상태로저장한다() {
        // given
        when(webhookEventRepository.existsByWebhookId(WEBHOOK_ID)).thenReturn(false);
        when(webhookEventRepository.saveAndFlush(any(PortoneWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                WEBHOOK_ID,
                new UnsupportedWebhook(),
                RAW_PAYLOAD
        );

        // then
        ArgumentCaptor<PortoneWebhookEvent> eventCaptor = ArgumentCaptor.forClass(PortoneWebhookEvent.class);
        verify(webhookEventRepository).saveAndFlush(eventCaptor.capture());
        PortoneWebhookEvent savedEvent = eventCaptor.getValue();

        assertThat(response.received()).isTrue();
        assertThat(response.processed()).isFalse();
        assertThat(response.portonePaymentId()).isNull();
        assertThat(response.reason()).isEqualTo("UNSUPPORTED_EVENT_TYPE");

        assertThat(savedEvent.getWebhookId()).isEqualTo(WEBHOOK_ID);
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookEventStatus.IGNORE);
        assertThat(savedEvent.getType()).isEqualTo("UnsupportedWebhook");
        assertThat(savedEvent.getPortonePaymentId()).isNull();
        assertThat(savedEvent.getRawPayload()).isEqualTo(RAW_PAYLOAD);
        assertThat(savedEvent.getFailureReason()).isEqualTo("UNSUPPORTED_EVENT_TYPE");
        assertThat(savedEvent.getProcessedAt()).isNotNull();
    }

    @Test
    void 웹훅수신_저장중Unique충돌이고이미존재하면_중복응답한다() {
        // given
        when(webhookEventRepository.existsByWebhookId(WEBHOOK_ID)).thenReturn(false, true);
        when(webhookEventRepository.saveAndFlush(any(PortoneWebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate webhook id"));

        // when
        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                WEBHOOK_ID,
                paidWebhook("pay_123"),
                RAW_PAYLOAD
        );

        // then
        assertThat(response.received()).isTrue();
        assertThat(response.processed()).isFalse();
        assertThat(response.portonePaymentId()).isNull();
        assertThat(response.reason()).isEqualTo("DUPLICATE_WEBHOOK_ID");

        verify(webhookEventRepository, times(2)).existsByWebhookId(WEBHOOK_ID);
        verify(webhookEventRepository).saveAndFlush(any(PortoneWebhookEvent.class));
    }

    private WebhookTransactionPaid paidWebhook(String paymentId) {
        WebhookTransactionDataPaid data = new WebhookTransactionDataPaid(
                paymentId,
                "store-123",
                "transaction-123"
        );

        return new WebhookTransactionPaid(Instant.parse("2026-05-29T09:35:00Z"), data);
    }

    private static class UnsupportedWebhook implements Webhook {
    }
}
