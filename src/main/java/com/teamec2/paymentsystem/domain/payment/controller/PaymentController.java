package com.teamec2.paymentsystem.domain.payment.controller;

import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentRequest;
import com.teamec2.paymentsystem.domain.payment.dto.ConfirmPaymentResponse;
import com.teamec2.paymentsystem.domain.payment.service.PaymentService;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import com.teamec2.paymentsystem.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 인증 사용자의 결제 확정 요청을 처리한다.
     *
     * @param userDetails JWT 인증으로 식별된 사용자 정보
     * @param request 결제 확정 요청 정보
     * @return 결제 확정 결과 응답
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<ConfirmPaymentResponse>> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        ConfirmPaymentResponse response = paymentService.confirmPayment(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}
