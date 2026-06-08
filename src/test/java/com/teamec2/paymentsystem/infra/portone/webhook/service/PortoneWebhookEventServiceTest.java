package com.teamec2.paymentsystem.infra.portone.webhook.service;

import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;
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
import io.portone.sdk.server.webhook.WebhookTransactionCancelledDataPartialCancelled;
import io.portone.sdk.server.webhook.WebhookTransactionCancelledPartialCancelled;
import io.portone.sdk.server.webhook.WebhookTransactionDataFailed;
import io.portone.sdk.server.webhook.WebhookTransactionDataPaid;
import io.portone.sdk.server.webhook.WebhookTransactionFailed;
import io.portone.sdk.server.webhook.WebhookTransactionPaid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
    private static final String RAW_REFUND_PAYLOAD = """
            {
              "type": "Transaction.PartialCancelled",
              "data": {
                "paymentId": "pay_123",
                "cancellationId": "cancel_123"
              }
            }
            """;

    @Mock
    PortoneWebhookEventRepository webhookEventRepository;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    PaymentFacade paymentFacade;

    @Mock
    RefundProcessingTxService refundProcessingTxService;

    @InjectMocks
    PortoneWebhookEventService portoneWebhookEventService;

    @Test
    void 웹훅수신_TransactionPaid이면_결제확정후_PROCESSED상태로저장한다() {
        // given
        Payment payment = mock(Payment.class);
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any(PortoneWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentFacade.confirmPaidWebhook("pay_123"))
                .thenReturn(결제확정응답("pay_123"));
        when(paymentRepository.findById(300L)).thenReturn(Optional.of(payment));

        // when
        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                WEBHOOK_ID,
                paidWebhook("pay_123"),
                RAW_PAYLOAD
        );

        // then
        ArgumentCaptor<PortoneWebhookEvent> eventCaptor = ArgumentCaptor.forClass(PortoneWebhookEvent.class);
        verify(webhookEventRepository, times(2)).saveAndFlush(eventCaptor.capture());
        PortoneWebhookEvent savedEvent = eventCaptor.getValue();

        assertThat(response.received()).isTrue();
        assertThat(response.processed()).isTrue();
        assertThat(response.portonePaymentId()).isEqualTo("pay_123");
        assertThat(response.reason()).isEqualTo("PROCESSED");

        assertThat(savedEvent.getWebhookId()).isEqualTo(WEBHOOK_ID);
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(savedEvent.getPayment()).isSameAs(payment);
        assertThat(savedEvent.getType()).isEqualTo("Transaction.Paid");
        assertThat(savedEvent.getPortonePaymentId()).isEqualTo("pay_123");
        assertThat(savedEvent.getRawPayload()).isEqualTo(RAW_PAYLOAD);
        assertThat(savedEvent.getFailureReason()).isNull();
        assertThat(savedEvent.getProcessedAt()).isNotNull();
        verify(paymentFacade).confirmPaidWebhook("pay_123");
        verify(paymentRepository).findById(300L);
    }

    @Test
    void 웹훅수신_결제확정실패하면_FAILED상태와실패사유를저장하고예외를던진다() {
        // given
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any(PortoneWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentFacade.confirmPaidWebhook("pay_123"))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        // when
        // then
        assertThatThrownBy(() -> portoneWebhookEventService.receive(
                WEBHOOK_ID,
                paidWebhook("pay_123"),
                RAW_PAYLOAD
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);

        ArgumentCaptor<PortoneWebhookEvent> eventCaptor = ArgumentCaptor.forClass(PortoneWebhookEvent.class);
        verify(webhookEventRepository, times(2)).saveAndFlush(eventCaptor.capture());
        PortoneWebhookEvent savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getWebhookId()).isEqualTo(WEBHOOK_ID);
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookEventStatus.FAILED);
        assertThat(savedEvent.getType()).isEqualTo("Transaction.Paid");
        assertThat(savedEvent.getPortonePaymentId()).isEqualTo("pay_123");
        assertThat(savedEvent.getFailureReason()).isEqualTo("PAYMENT_NOT_FOUND");
        assertThat(savedEvent.getProcessedAt()).isNotNull();
        verify(paymentFacade).confirmPaidWebhook("pay_123");
        verify(paymentRepository, never()).findById(any());
    }

    @Test
    void 웹훅수신_TransactionPaid인데_paymentId가비어있으면_WEBHOOK_PAYMENT_ID_MISSING이발생한다() {
        // given
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());

        // when
        // then
        assertThatThrownBy(() -> portoneWebhookEventService.receive(
                WEBHOOK_ID,
                paidWebhook(" "),
                RAW_PAYLOAD
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WEBHOOK_PAYMENT_ID_MISSING);

        verify(webhookEventRepository).findByWebhookId(WEBHOOK_ID);
        verify(webhookEventRepository, never()).saveAndFlush(any(PortoneWebhookEvent.class));
        verify(paymentFacade, never()).confirmPaidWebhook(any());
    }

    @Test
    void 웹훅수신_이미저장된WebhookId이면_중복응답하고저장하지않는다() {
        // given
        PortoneWebhookEvent ignoredEvent = PortoneWebhookEvent.ignored(
                WEBHOOK_ID,
                "UnsupportedWebhook",
                null,
                RAW_PAYLOAD,
                "UNSUPPORTED_EVENT_TYPE"
        );
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.of(ignoredEvent));

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
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());
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
    void 웹훅수신_TransactionFailed이면_PortOne이벤트명으로_IGNORE상태저장한다() {
        // given
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any(PortoneWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                WEBHOOK_ID,
                failedWebhook("pay_123"),
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

        assertThat(savedEvent.getStatus()).isEqualTo(WebhookEventStatus.IGNORE);
        assertThat(savedEvent.getType()).isEqualTo("Transaction.Failed");
        assertThat(savedEvent.getPortonePaymentId()).isEqualTo("pay_123");
        assertThat(savedEvent.getFailureReason()).isEqualTo("UNSUPPORTED_EVENT_TYPE");
        assertThat(savedEvent.getProcessedAt()).isNotNull();
    }

    @Test
    void 웹훅수신_저장중Unique충돌이고이미존재하면_중복응답한다() {
        // given
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());
        when(webhookEventRepository.existsByWebhookId(WEBHOOK_ID)).thenReturn(true);
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

        verify(webhookEventRepository).existsByWebhookId(WEBHOOK_ID);
        verify(webhookEventRepository, times(2)).findByWebhookId(WEBHOOK_ID);
        verify(webhookEventRepository).saveAndFlush(any(PortoneWebhookEvent.class));
    }

    private ConfirmPaymentResponse 결제확정응답(String portonePaymentId) {
        return new ConfirmPaymentResponse(
                200L,
                "ORDER-20260529-000001",
                OrderStatus.COMPLETED,
                300L,
                portonePaymentId,
                PaymentStatus.COMPLETED,
                PaymentType.CARD,
                1000L,
                0L,
                1000L,
                10L,
                true,
                OffsetDateTime.parse("2026-05-29T18:35:00+09:00")
        );
    }

    private WebhookTransactionPaid paidWebhook(String paymentId) {
        WebhookTransactionDataPaid data = new WebhookTransactionDataPaid(
                paymentId,
                "store-123",
                "transaction-123"
        );

        return new WebhookTransactionPaid(Instant.parse("2026-05-29T09:35:00Z"), data);
    }

    private WebhookTransactionFailed failedWebhook(String paymentId) {
        WebhookTransactionDataFailed data = new WebhookTransactionDataFailed(
                paymentId,
                "store-123",
                "transaction-123"
        );

        return new WebhookTransactionFailed(Instant.parse("2026-05-29T09:35:00Z"), data);
    }

    @Test
    void receive_partial_cancelled_webhook_processes_refund_by_cancellation_id() {
        // given
        Payment payment = mock(Payment.class);
        when(webhookEventRepository.findByWebhookId(WEBHOOK_ID)).thenReturn(Optional.empty());
        when(webhookEventRepository.saveAndFlush(any(PortoneWebhookEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.findByPortonePaymentId("pay_123")).thenReturn(Optional.of(payment));

        // when
        PortoneWebhookReceiveResponse response = portoneWebhookEventService.receive(
                WEBHOOK_ID,
                partialCancelledWebhook("pay_123", "cancel_123"),
                RAW_REFUND_PAYLOAD
        );

        // then
        ArgumentCaptor<PortoneWebhookEvent> eventCaptor = ArgumentCaptor.forClass(PortoneWebhookEvent.class);
        verify(webhookEventRepository, times(2)).saveAndFlush(eventCaptor.capture());
        PortoneWebhookEvent savedEvent = eventCaptor.getValue();

        assertThat(response.received()).isTrue();
        assertThat(response.processed()).isTrue();
        assertThat(response.portonePaymentId()).isEqualTo("pay_123");
        assertThat(response.reason()).isEqualTo("PROCESSED");

        assertThat(savedEvent.getWebhookId()).isEqualTo(WEBHOOK_ID);
        assertThat(savedEvent.getStatus()).isEqualTo(WebhookEventStatus.PROCESSED);
        assertThat(savedEvent.getPayment()).isSameAs(payment);
        assertThat(savedEvent.getType()).isEqualTo("Transaction.PartialCancelled");
        assertThat(savedEvent.getPortonePaymentId()).isEqualTo("pay_123");
        assertThat(savedEvent.getRawPayload()).isEqualTo(RAW_REFUND_PAYLOAD);
        assertThat(savedEvent.getFailureReason()).isNull();
        assertThat(savedEvent.getProcessedAt()).isNotNull();

        verify(refundProcessingTxService).completeByPortoneCancellationId("pay_123", "cancel_123");
        verify(paymentRepository).findByPortonePaymentId("pay_123");
        verify(paymentFacade, never()).confirmPaidWebhook(any());
    }

    private WebhookTransactionCancelledPartialCancelled partialCancelledWebhook(
            String paymentId,
            String cancellationId
    ) {
        WebhookTransactionCancelledDataPartialCancelled data = new WebhookTransactionCancelledDataPartialCancelled(
                paymentId,
                "store-123",
                "transaction-123",
                cancellationId
        );

        return new WebhookTransactionCancelledPartialCancelled(Instant.parse("2026-05-29T09:35:00Z"), data);
    }

    private static class UnsupportedWebhook implements Webhook {
    }
}
