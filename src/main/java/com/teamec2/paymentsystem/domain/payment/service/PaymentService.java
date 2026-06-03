package com.teamec2.paymentsystem.domain.payment.service;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.repository.OrderRepository;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGateway;
import com.teamec2.paymentsystem.domain.payment.port.PaymentGatewayResponse;
import com.teamec2.paymentsystem.domain.payment.repository.PaymentRepository;
import com.teamec2.paymentsystem.domain.point.service.PointService;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PaymentService {

    // 결제 확정 흐름에서는 아직 장바구니를 자동 정리하지 않으므로 false로 반환한다.
    private static final boolean CART_CLEARED = false;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;

    private final PointService pointService;

    /**
     * PortOne 결제 조회 결과를 검증한 뒤 주문과 결제를 완료 상태로 변경한다.
     *
     * <p>이미 완료된 결제는 상태를 다시 변경하지 않고 현재 확정 결과를 반환한다.
     * <p>PG 결제 금액이 없는 포인트 전액 결제는 외부 결제 조회 없이 확정한다.
     *
     * @param userId 결제 확정을 요청한 사용자 ID
     * @param request 결제 확정 요청 정보
     * @return 결제 확정 결과
     */
    @Transactional
    public ConfirmPaymentResponse confirmPayment(Long userId, ConfirmPaymentRequest request) {
        Order order = findOrder(request.orderId());
        validateOrderOwner(order, userId);

        Payment payment = findPaymentForConfirm(order.getId());
        validateRequestedPayment(payment, request.portonePaymentId());

        if (payment.isCompleted()) {
            return toConfirmPaymentResponse(order, payment);
        }

        validateConfirmable(order, payment);

        LocalDateTime approvedAt = resolveApprovedAtAfterVerification(payment);

        // 이 시점에 포인트 사용, 결제 완료, 포인트 적립까지 함께 처리합니다.
        completePayment(order, payment, approvedAt);

        return toConfirmPaymentResponse(order, payment);
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }

    private void validateOrderOwner(Order order, Long userId) {
        if (!Objects.equals(order.getUser().getId(), userId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private Payment findPaymentForConfirm(Long orderId) {
        return paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void validateRequestedPayment(Payment payment, String portonePaymentId) {
        if (!payment.getPortonePaymentId().equals(portonePaymentId)) {
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ID_MISMATCH);
        }
    }

    private void validateConfirmable(Order order, Payment payment) {
        validateOrderPaymentPending(order);
        validatePaymentPending(payment);
    }

    private void validateOrderPaymentPending(Order order) {
        if (!order.isPaymentPending()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    private void validatePaymentPending(Payment payment) {
        if (!payment.isPending()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }

    private LocalDateTime resolveApprovedAtAfterVerification(Payment payment) {
        if (payment.isPointOnly()) {
            // 포인트 전액 결제는 PG 승인 시각이 없으므로 내부 확정 시각을 승인 시각으로 사용한다.
            return LocalDateTime.now();
        }

        PaymentGatewayResponse gatewayResponse = paymentGateway.getPayment(payment.getPortonePaymentId());
        validateGatewayPayment(payment, gatewayResponse);
        return gatewayResponse.approvedAt();
    }

    private void validateGatewayPayment(Payment payment, PaymentGatewayResponse gatewayResponse) {
        validateGatewayPaymentId(payment, gatewayResponse);
        validateGatewayPaymentStatus(gatewayResponse);
        validateGatewayPaymentAmount(payment, gatewayResponse);
        validateGatewayApprovedAt(gatewayResponse);
    }

    private void validateGatewayPaymentId(Payment payment, PaymentGatewayResponse gatewayResponse) {
        if (!gatewayResponse.hasSamePaymentId(payment.getPortonePaymentId())) {
            throw new BusinessException(ErrorCode.PAYMENT_PORTONE_ID_MISMATCH);
        }
    }

    private void validateGatewayPaymentStatus(PaymentGatewayResponse gatewayResponse) {
        if (!gatewayResponse.isPaid()) {
            throw new BusinessException(ErrorCode.PAYMENT_STATUS_NOT_PAID);
        }
    }

    private void validateGatewayPaymentAmount(Payment payment, PaymentGatewayResponse gatewayResponse) {
        if (!gatewayResponse.hasSameAmount(payment.getPgAmount())) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void validateGatewayApprovedAt(PaymentGatewayResponse gatewayResponse) {
        if (gatewayResponse.approvedAt() == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_FAILED);
        }
    }

    private void completePayment(Order order, Payment payment, LocalDateTime approvedAt) {
        // 결제 시 사용한 포인트 차감
        pointService.confirmReservedPoints(payment);

        payment.complete(approvedAt);
        order.complete();

        // PG 실결제 시 금액의 1% 포인트 적립
        pointService.earnPoints(payment);
    }

    private ConfirmPaymentResponse toConfirmPaymentResponse(Order order, Payment payment) {
        return ConfirmPaymentResponse.from(order, payment, CART_CLEARED);
    }
}
