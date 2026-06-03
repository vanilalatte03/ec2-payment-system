package com.teamec2.paymentsystem.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 결제 확정을 요청할 때 사용하는 DTO.
 *
 * @param orderId 결제 확정 대상 주문 ID
 * @param portonePaymentId 주문 생성 시 발급된 PortOne 결제 ID
 */
public record ConfirmPaymentRequest(
        @NotNull Long orderId,
        @NotBlank String portonePaymentId
) {

}

