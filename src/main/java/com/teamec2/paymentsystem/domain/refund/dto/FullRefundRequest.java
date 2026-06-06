package com.teamec2.paymentsystem.domain.refund.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 전체 환불 요청 DTO입니다.
 */
public record FullRefundRequest(

        @NotBlank(message = "환불 사유는 필수입니다.")
        @Size(max = 255, message = "환불 사유는 255자를 초과할 수 없습니다.")
        String reason
) {
}

