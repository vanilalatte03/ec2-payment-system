package com.teamec2.paymentsystem.domain.refund.service;

import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentCancelStatus;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundProcessorTest {

    @Mock
    RefundProcessingTxService refundProcessingTxService;

    @Mock
    PaymentGateway paymentGateway;

    RefundProcessor refundProcessor;

    @BeforeEach
    void setUp() {
        refundProcessor = new RefundProcessor(refundProcessingTxService, paymentGateway);
    }

    @Test
    void 환불처리_취소성공이면_cancellationId로_환불완료처리를_호출한다() {
        // given
        Long outboxId = 10L;
        RefundCancelCommand command =
                new RefundCancelCommand(20L, "pay_123", 2_700L, 9_000L, "partial refund");

        when(refundProcessingTxService.start(outboxId)).thenReturn(Optional.of(command));
        when(paymentGateway.cancelPayment(
                "pay_123",
                2_700L,
                9_000L,
                "partial refund",
                "refund-cancel-request-20"
        )).thenReturn(new PaymentCancelResponse(
                "cancellation-123",
                "SUCCEEDED",
                PaymentCancelStatus.SUCCEEDED
        ));

        // when
        refundProcessor.process(outboxId);

        // then
        verify(refundProcessingTxService).complete(outboxId, "cancellation-123");
        verify(refundProcessingTxService, never()).complete(outboxId);
    }

    @Test
    void 환불처리_취소결과미확정이면_cancellationId로_재시도를_예약한다() {
        // given
        Long outboxId = 10L;
        RefundCancelCommand command =
                new RefundCancelCommand(20L, "pay_123", 2_700L, 9_000L, "partial refund");

        when(refundProcessingTxService.start(outboxId)).thenReturn(Optional.of(command));
        when(paymentGateway.cancelPayment(
                "pay_123",
                2_700L,
                9_000L,
                "partial refund",
                "refund-cancel-request-20"
        )).thenReturn(new PaymentCancelResponse(
                "cancellation-123",
                "REQUESTED",
                PaymentCancelStatus.RESULT_UNKNOWN
        ));

        // when
        refundProcessor.process(outboxId);

        // then
        verify(refundProcessingTxService).retryAsPgResultUnknown(
                outboxId,
                "cancellation-123",
                "PortOne 취소 결과 미확정 상태: REQUESTED"
        );
        verify(refundProcessingTxService, never()).complete(outboxId, "cancellation-123");
    }

    @Test
    void 환불처리_취소실패가확정되면_환불실패처리를_호출한다() {
        // given
        Long outboxId = 10L;
        RefundCancelCommand command =
                new RefundCancelCommand(20L, "pay_123", 600L, 1_000L, "partial refund");

        when(refundProcessingTxService.start(outboxId)).thenReturn(Optional.of(command));
        when(paymentGateway.cancelPayment(
                "pay_123",
                600L,
                1_000L,
                "partial refund",
                "refund-cancel-request-20"
        )).thenReturn(new PaymentCancelResponse(
                null,
                "PG_PROVIDER: 50053: 간편결제 부분취소 제한 가맹점",
                PaymentCancelStatus.FAILED
        ));

        // when
        refundProcessor.process(outboxId);

        // then
        verify(refundProcessingTxService).fail(
                outboxId,
                "PortOne 취소 실패 상태: PG_PROVIDER: 50053: 간편결제 부분취소 제한 가맹점"
        );
        verify(refundProcessingTxService, never()).retryAsPgResultUnknown(
                outboxId,
                null,
                "PortOne 취소 결과 미확정 상태: PG_PROVIDER: 50053: 간편결제 부분취소 제한 가맹점"
        );
        verify(refundProcessingTxService, never()).complete(outboxId, null);
    }

    @Test
    void 환불처리_선점할Outbox가없으면_PG취소를호출하지않는다() {
        // given
        Long outboxId = 10L;
        when(refundProcessingTxService.start(outboxId)).thenReturn(Optional.empty());

        // when
        refundProcessor.process(outboxId);

        // then
        verify(paymentGateway, never()).cancelPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(refundProcessingTxService, never()).complete(outboxId);
    }

    @Test
    void 환불처리_PG환불금액이0원이면_PG취소없이_환불완료처리를_호출한다() {
        // given
        Long outboxId = 10L;
        RefundCancelCommand command =
                new RefundCancelCommand(20L, "pay_123", 0L, 0L, "point only refund");

        when(refundProcessingTxService.start(outboxId)).thenReturn(Optional.of(command));

        // when
        refundProcessor.process(outboxId);

        // then
        verify(refundProcessingTxService).complete(outboxId);
        verify(paymentGateway, never()).cancelPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void 환불처리_PG호출예외면_결과미확정으로_재시도예약한다() {
        // given
        Long outboxId = 10L;
        RefundCancelCommand command =
                new RefundCancelCommand(20L, "pay_123", 2_700L, 9_000L, "partial refund");

        when(refundProcessingTxService.start(outboxId)).thenReturn(Optional.of(command));
        when(paymentGateway.cancelPayment(
                "pay_123",
                2_700L,
                9_000L,
                "partial refund",
                "refund-cancel-request-20"
        )).thenThrow(new RuntimeException("PortOne timeout"));

        // when
        refundProcessor.process(outboxId);

        // then
        verify(refundProcessingTxService).retryAsPgResultUnknown(outboxId, "PortOne timeout");
        verify(refundProcessingTxService, never()).fail(
                org.mockito.ArgumentMatchers.eq(outboxId),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
