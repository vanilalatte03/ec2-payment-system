package com.teamec2.paymentsystem.domain.order.controller;

import com.teamec2.paymentsystem.domain.order.dto.CreateOrderRequest;
import com.teamec2.paymentsystem.domain.order.dto.CreateOrderResponse;
import com.teamec2.paymentsystem.domain.order.service.OrderService;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import com.teamec2.paymentsystem.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 주문서에서 "결제하기" 버튼을 눌렀을 때 호출되는 주문 생성 API입니다.
    // 이 API는 주문과 결제 대기 레코드를 함께 만들고, 실제 결제창에 필요한 portonePaymentId를 응답합니다.
    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CreateOrderResponse response = orderService.createOrder(
                userDetails.getUserId(),
                request.cartItemIds(),
                request.usePointAmount()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
