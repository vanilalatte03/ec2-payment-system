package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.facade.PaymentFacade;
import com.teamec2.paymentsystem.domain.payment.port.PaymentCancelStatus;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceUnitTest {

    @Mock
    PaymentConfirmTxService paymentConfirmTxService;

    @Mock
    PaymentGateway paymentGateway;

    PaymentService paymentService;

    PaymentFacade paymentFacade;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentGateway);
        paymentFacade = new PaymentFacade(paymentConfirmTxService, paymentService);
    }

    @Test
    void 결제확정_외부결제성공후_내부완료실패면_보상취소하고_원예외를전파한다() {
        // given
        Long userId = 1L;
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(10L, "pay_123");
        ConfirmPaymentTarget target = new ConfirmPaymentTarget(20L, "pay_123", 800L, false, null);
        LocalDateTime approvedAt = LocalDateTime.of(2026, 6, 1, 12, 30);
        PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse("pay_123", "PAID", 800L, approvedAt);
        BusinessException completeFailure = new BusinessException(ErrorCode.CONFLICT);

        when(paymentConfirmTxService.prepare(userId, request)).thenReturn(target);
        when(paymentGateway.getPayment("pay_123")).thenReturn(gatewayResponse);
        when(paymentConfirmTxService.complete(20L, approvedAt)).thenThrow(completeFailure);
        when(paymentGateway.cancelPayment(
                "pay_123",
                800L,
                800L,
                "PAYMENT_CONFIRM_INTERNAL_FAILURE",
                "payment-confirm-compensation-20"
        )).thenReturn(new PaymentCancelResponse("cancel_123", "SUCCEEDED", PaymentCancelStatus.SUCCEEDED));

        // when
        // then
        assertThatThrownBy(() -> paymentFacade.confirmPayment(userId, request))
                .isSameAs(completeFailure);

        verify(paymentGateway).cancelPayment(
                "pay_123",
                800L,
                800L,
                "PAYMENT_CONFIRM_INTERNAL_FAILURE",
                "payment-confirm-compensation-20"
        );
        verify(paymentConfirmTxService).failAfterCompensation(20L);
    }
}
