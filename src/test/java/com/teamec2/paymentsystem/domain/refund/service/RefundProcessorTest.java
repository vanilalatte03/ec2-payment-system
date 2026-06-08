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
}
