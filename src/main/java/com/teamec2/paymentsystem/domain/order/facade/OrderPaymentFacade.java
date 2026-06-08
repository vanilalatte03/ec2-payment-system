package com.teamec2.paymentsystem.domain.order.facade;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaymentFacade {

    private final PaymentRepository paymentRepository;

    public Payment getPayment(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    public Payment getPaymentWithLock(Long orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    public Payment savePendingPayment(
            Order order,
            Long totalAmount,
            Long usedPointAmount,
            Long pgAmount,
            Long rewardPointAmount
    ) {
        Payment payment = Payment.createPending(
                order,
                totalAmount,
                usedPointAmount,
                pgAmount,
                rewardPointAmount
        );

        return paymentRepository.save(payment);
    }
}
