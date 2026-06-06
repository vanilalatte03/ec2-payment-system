package com.teamec2.paymentsystem.domain.refund.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 부분 환불 요청 DTO입니다.
 */
public record PartialRefundRequest(

        @NotBlank(message = "환불 사유는 필수입니다.")
        @Size(max = 255, message = "환불 사유는 255자를 초과할 수 없습니다.")
        String reason,

        @NotEmpty(message = "부분 환불 상품 목록은 필수입니다.")
        List<@Valid RefundItemRequest> items
) {
}
