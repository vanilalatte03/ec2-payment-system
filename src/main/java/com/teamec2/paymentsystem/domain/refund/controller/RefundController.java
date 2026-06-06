package com.teamec2.paymentsystem.domain.refund.controller;

import com.teamec2.paymentsystem.domain.refund.dto.FullRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.PartialRefundRequest;
import com.teamec2.paymentsystem.domain.refund.dto.RefundResponse;
import com.teamec2.paymentsystem.domain.refund.service.RefundService;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import com.teamec2.paymentsystem.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 환불 요청 API 컨트롤러입니다.
 *
 * 실제 PG 환불은 이 요청에서 바로 수행하지 않고,
 * Refund / RefundItem / RefundOutbox 스냅샷을 생성한 뒤 스케줄러가 Outbox를 처리하는 구조입니다.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class RefundController {

    private final RefundService refundService;
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /**
     * 부분 환불 요청입니다.
     * 주문 내 특정 OrderItem과 수량을 지정해서 환불 요청을 생성합니다.
     */
    @PostMapping("/orders/{orderId}/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> requestPartialRefund(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("orderId") Long orderId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody PartialRefundRequest partialRefundRequest
    ) {
        RefundResponse refundResponse = refundService.requestPartialRefund(
                userDetails.getUserId(),
                orderId,
                idempotencyKey,
                partialRefundRequest
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(refundResponse));
    }

    /**
     * 전체 환불 요청입니다.
     * 이미 일부 상품이 환불된 결제라면, 남아 있는 환불 가능 수량만 전체 환불 대상으로 잡습니다.
     */
    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> requestFullRefund(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("paymentId") Long paymentId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody FullRefundRequest fullRefundRequest
    ) {
        RefundResponse refundResponse = refundService.requestFullRefund(
                userDetails.getUserId(),
                paymentId,
                idempotencyKey,
                fullRefundRequest
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(refundResponse));
    }
}
