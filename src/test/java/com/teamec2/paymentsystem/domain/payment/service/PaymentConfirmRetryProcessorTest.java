package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmRetryProcessorTest {

    @Mock
    PaymentConfirmTxService paymentConfirmTxService;

    @InjectMocks
    PaymentConfirmRetryProcessor paymentConfirmRetryProcessor;

    @Test
    void 완료재시도중_상태충돌이면_보상취소대상으로전환한다() {
        // given
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        PaymentConfirmRetryCommand command = new PaymentConfirmRetryCommand(1L, approvedAt, 800L);
        BusinessException completeFailure = new BusinessException(ErrorCode.CONFLICT);

        when(paymentConfirmTxService.startConfirmRetry(1L)).thenReturn(Optional.of(command));
        when(paymentConfirmTxService.complete(1L, approvedAt)).thenThrow(completeFailure);

        // when
        paymentConfirmRetryProcessor.process(1L);

        // then
        verify(paymentConfirmTxService).markCompensationRequired(
                1L,
                800L,
                ErrorCode.CONFLICT.getMessage()
        );
        verify(paymentConfirmTxService, never()).retryConfirm(1L, ErrorCode.CONFLICT.getMessage());
    }

    @Test
    void 완료재시도중_일시장애면_다음완료재시도를예약한다() {
        // given
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        PaymentConfirmRetryCommand command = new PaymentConfirmRetryCommand(1L, approvedAt, 800L);
        RuntimeException completeFailure = new IllegalStateException("temporary db failure");

        when(paymentConfirmTxService.startConfirmRetry(1L)).thenReturn(Optional.of(command));
        when(paymentConfirmTxService.complete(1L, approvedAt)).thenThrow(completeFailure);

        // when
        paymentConfirmRetryProcessor.process(1L);

        // then
        verify(paymentConfirmTxService).retryConfirm(1L, "temporary db failure");
        verify(paymentConfirmTxService, never()).markCompensationRequired(1L, 800L, "temporary db failure");
    }
}
