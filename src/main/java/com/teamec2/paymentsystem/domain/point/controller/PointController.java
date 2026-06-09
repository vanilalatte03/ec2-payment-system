package com.teamec2.paymentsystem.domain.point.controller;

import com.teamec2.paymentsystem.domain.point.dto.PointBalanceResponse;
import com.teamec2.paymentsystem.domain.point.dto.PointTransactionResponse;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.service.PointQueryService;
import com.teamec2.paymentsystem.global.pagination.PageResponse;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.teamec2.paymentsystem.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/points")
public class PointController {

    private final PointQueryService pointQueryService;

    /**
     * JWT 인증을 마친 현재 로그인 사용자의 ID의 포인트 잔액을 조회합니다.
     */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<PointBalanceResponse>> getPointBalance(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getUserId();

        PointBalanceResponse pointBalanceResponse = pointQueryService.getPointBalance(userId);
        return ResponseEntity.ok(ApiResponse.success(pointBalanceResponse));
    }

    /**
     * JWT 인증을 마친 현재 로그인 사용자의 ID의 포인트 거래 내역을 최신순으로 조회합니다.
     */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<PointTransactionResponse>>> getPointTransaction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) PointTransactionType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = userDetails.getUserId();

        PageResponse<PointTransactionResponse> pointTransactionResponse = pointQueryService.getPointTransaction(userId, type, page, size);
        return ResponseEntity.ok(ApiResponse.success(pointTransactionResponse));
    }
}
