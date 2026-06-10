package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentConfirmRetryProcessor {

    private final PaymentConfirmTxService paymentConfirmTxService;

    /**
     * 외부 결제 성공 후 내부 완료 처리에 실패한 결제를 다시 완료 처리합니다.
     */
    public void process(Long paymentId) {
        PaymentConfirmRetryCommand command = paymentConfirmTxService.startConfirmRetry(paymentId)
                .orElse(null);

        if (command == null) {
            return;
        }

        try {
            paymentConfirmTxService.complete(command.paymentId(), command.approvedAt());
        } catch (RuntimeException e) {
            if (shouldCompensate(e)) {
                paymentConfirmTxService.markCompensationRequired(
                        command.paymentId(),
                        command.cancelAmount(),
                        e.getMessage()
                );
                return;
            }

            paymentConfirmTxService.retryConfirm(command.paymentId(), e.getMessage());
        }
    }

    public void recoverStaleProcessing(Long paymentId) {
        paymentConfirmTxService.retryConfirm(
                paymentId,
                "PROCESSING 상태가 오래 지속되어 내부 결제 완료 결과를 확정하지 못했습니다."
        );
    }

    private boolean shouldCompensate(RuntimeException e) {
        if (!(e instanceof BusinessException businessException)) {
            return false;
        }

        ErrorCode errorCode = businessException.getErrorCode();

        return errorCode == ErrorCode.INVALID_ORDER_STATUS
                || errorCode == ErrorCode.ORDER_CANCEL_NOT_ALLOWED
                || errorCode == ErrorCode.CONFLICT;
    }
}
