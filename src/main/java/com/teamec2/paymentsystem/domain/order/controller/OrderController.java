package com.teamec2.paymentsystem.domain.order.controller;

import com.teamec2.paymentsystem.domain.order.dto.CancelRequest;
import com.teamec2.paymentsystem.domain.order.dto.CancelResponse;
import com.teamec2.paymentsystem.domain.order.dto.CreateRequest;
import com.teamec2.paymentsystem.domain.order.dto.CreateResponse;
import com.teamec2.paymentsystem.domain.order.dto.OrderDetailResponse;
import com.teamec2.paymentsystem.domain.order.dto.OrderListResponse;
import com.teamec2.paymentsystem.domain.order.dto.OrderPreviewResponse;
import com.teamec2.paymentsystem.domain.order.service.OrderService;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import com.teamec2.paymentsystem.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 결제하기 직전에 장바구니 상품을 주문서 형태로 미리 확인하는 읽기 전용 API입니다.
    // 주문/결제/주문상품 스냅샷은 만들지 않고, 현재 상품 가격으로 합계를 계산합니다.
    @GetMapping("/preview")
    public ResponseEntity<ApiResponse<OrderPreviewResponse>> previewOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) List<Long> cartItemIds
    ) {
        OrderPreviewResponse response = orderService.previewOrder(
                userDetails.getUserId(),
                cartItemIds
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 주문서에서 "결제하기" 버튼을 눌렀을 때 호출되는 주문 생성 API입니다.
    // 이 API는 주문과 결제 대기 레코드를 함께 만들고, 실제 결제창에 필요한 portonePaymentId를 응답합니다.
    @PostMapping
    public ResponseEntity<ApiResponse<CreateResponse>> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateRequest request
    ) {
        CreateResponse response = orderService.createOrder(
                userDetails.getUserId(),
                request.cartItemIds(),
                request.usedPointAmount()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OrderListResponse>> findMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        OrderListResponse response = orderService.findMyOrders(userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> findMyOrderDetail(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        OrderDetailResponse response = orderService.findMyOrderDetail(
                userDetails.getUserId(),
                orderId
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<CancelResponse>> cancelOrder(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody(required = false) CancelRequest request
    ) {
        CancelResponse response = orderService.cancelOrderByRequest(
                userDetails.getUserId(),
                orderId,
                request
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
