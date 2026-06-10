package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.payment.dto.PaymentCancelResponse;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentCompensationProcessor {

    private final PaymentConfirmTxService paymentConfirmTxService;
    private final PaymentGateway paymentGateway;

    /**
     * 결제 확정 보상 취소를 처리합니다.
     *
     * <p>PG 취소 성공이 확인된 경우에만 내부 주문/결제를 실패 상태로 정리합니다.
     * 예외나 결과 불명확 상태는 실패로 확정하지 않고 재시도 대상으로 남깁니다.
     */
    public void process(Long outboxId) {
        PaymentCompensationCommand command = paymentConfirmTxService.startCompensation(outboxId)
                .orElse(null);

        if (command == null) {
            return;
        }

        PaymentCancelResponse response;

        try {
            response = paymentGateway.cancelPayment(
                    command.portonePaymentId(),
                    command.cancelAmount(),
                    command.currentCancellableAmount(),
                    command.reason(),
                    command.idempotencyKey()
            );
        } catch (RuntimeException e) {
            paymentConfirmTxService.retryCompensation(
                    command.outboxId(),
                    e.getMessage()
            );

            return;
        }

        handleCancelResponse(command.outboxId(), response);
    }

    public void recoverStaleProcessing(Long outboxId) {
        paymentConfirmTxService.retryCompensation(
                outboxId,
                "PROCESSING 상태가 오래 지속되어 PG 보상 취소 결과를 확정하지 못했습니다."
        );
    }

    private void handleCancelResponse(Long outboxId, PaymentCancelResponse response) {
        if (response == null) {
            paymentConfirmTxService.retryCompensation(
                    outboxId,
                    "PG 보상 취소 응답이 없습니다."
            );

            return;
        }

        if (response.isSucceeded()) {
            paymentConfirmTxService.completeCompensation(outboxId, response.cancellationId());
            return;
        }

        if (response.isResultUnknown()) {
            paymentConfirmTxService.retryCompensation(
                    outboxId,
                    "PortOne 보상 취소 결과 미확정 상태: " + response.rawStatus()
            );

            return;
        }

        if (response.isFailed()) {
            paymentConfirmTxService.failCompensation(
                    outboxId,
                    "PortOne 보상 취소 실패 상태: " + response.rawStatus()
            );

            return;
        }

        paymentConfirmTxService.retryCompensation(
                outboxId,
                "PortOne 보상 취소 응답 상태를 해석할 수 없습니다. status=" + response.rawStatus()
        );
    }
}
