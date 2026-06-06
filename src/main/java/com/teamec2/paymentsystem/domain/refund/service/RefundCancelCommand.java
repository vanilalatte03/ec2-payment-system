package com.teamec2.paymentsystem.domain.refund.service;

public record RefundCancelCommand(
        Long refundId,
        String portonePaymentId,
        Long pgRefundAmount,
        Long currentCancellableAmount,
        String reason
) {
    public String portoneIdempotencyKey() {
        return "refund-cancel-request-" + refundId;
    }
}
