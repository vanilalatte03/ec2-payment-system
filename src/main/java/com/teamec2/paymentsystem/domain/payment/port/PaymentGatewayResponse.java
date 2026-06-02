package com.teamec2.paymentsystem.domain.payment.port;

import java.time.LocalDateTime;

public record PaymentGatewayResponse(
        String paymentId,
        String status,
        Long paidAmount,
        LocalDateTime approvedAt
) {
    public boolean isPaid() {
        return "PAID".equals(status);
    }

    public boolean hasSameAmount(Long expectedAmount) {
        return paidAmount != null && paidAmount.equals(expectedAmount);
    }

    public boolean hasSamePaymentId(String expectedPaymentId) {
        return paymentId != null && paymentId.equals(expectedPaymentId);
    }
}