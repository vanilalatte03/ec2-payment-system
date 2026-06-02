package com.teamec2.paymentsystem.domain.payment.port;

public interface PaymentGateway {

    PaymentGatewayResponse getPayment(String paymentId);
}
