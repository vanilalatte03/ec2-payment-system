package com.teamec2.paymentsystem.domain.payment.dto;

import com.teamec2.paymentsystem.domain.order.entity.Order;
import com.teamec2.paymentsystem.domain.order.entity.OrderStatus;
import com.teamec2.paymentsystem.domain.payment.entity.Payment;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentStatus;
import com.teamec2.paymentsystem.domain.payment.entity.PaymentType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 결제 확정 결과를 클라이언트에 반환하는 DTO.
 *
 * @param orderId 주문 ID
 * @param orderNumber 주문 번호
 * @param orderStatus 결제 확정 후 주문 상태
 * @param paymentId 결제 ID
 * @param portonePaymentId PortOne 결제 ID
 * @param paymentStatus 결제 확정 후 결제 상태
 * @param paymentType 결제 수단 구성
 * @param totalAmount 총 결제 금액
 * @param usedPointAmount 사용 포인트 금액
 * @param pgAmount PG 결제 금액
 * @param rewardPointAmount 적립 예정 포인트 금액
 * @param cartCleared 장바구니 정리 완료 여부
 * @param approvedAt 결제 승인 일시
 */
public record ConfirmPaymentResponse(
        Long orderId,
        String orderNumber,
        OrderStatus orderStatus,
        Long paymentId,
        String portonePaymentId,
        PaymentStatus paymentStatus,
        PaymentType paymentType,
        Long totalAmount,
        Long usedPointAmount,
        Long pgAmount,
        Long rewardPointAmount,
        Boolean cartCleared,
        OffsetDateTime approvedAt
) {
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

    /**
     * 주문과 결제 엔티티를 결제 확정 응답으로 변환한다.
     *
     * @param order 확정 대상 주문
     * @param payment 확정 대상 결제
     * @param cartCleared 장바구니 정리 완료 여부
     * @return 결제 확정 응답 DTO
     */
    public static ConfirmPaymentResponse from(Order order, Payment payment, boolean cartCleared) {
        return new ConfirmPaymentResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                payment.getId(),
                payment.getPortonePaymentId(),
                payment.getStatus(),
                payment.getPaymentType(),
                payment.getTotalAmount(),
                payment.getUsedPointAmount(),
                payment.getPgAmount(),
                payment.getRewardPointAmount(),
                cartCleared,
                toKstOffsetDateTime(payment)
        );
    }

    private static OffsetDateTime toKstOffsetDateTime(Payment payment) {
        if (payment.getApprovedAt() == null) {
            return null;
        }

        return payment.getApprovedAt().atOffset(KST_OFFSET);
    }
}
