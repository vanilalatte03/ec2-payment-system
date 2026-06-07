package com.teamec2.paymentsystem.domain.cart.controller;

import com.teamec2.paymentsystem.domain.cart.dto.*;
import com.teamec2.paymentsystem.domain.cart.service.CartItemService;
import com.teamec2.paymentsystem.domain.cart.service.CartService;
import com.teamec2.paymentsystem.global.response.ApiResponse;
import com.teamec2.paymentsystem.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartItemService cartItemService;
    private final CartService cartService;

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<AddItemResponse>> addItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddItemRequest request
    ) {
        AddItemResponse response = cartItemService.addItem(
                userDetails.getUserId(),
                request.productId(),
                request.quantity()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CartResponse response = cartService.getCart(userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<UpdateQuantityResponse>> updateQuantity(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateQuantityRequest request
    ) {
        UpdateQuantityResponse response = cartItemService.updateQuantity(
                userDetails.getUserId(),
                cartItemId,
                request.quantity()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<DeleteItemResponse>> deleteItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cartItemId
    ) {
        DeleteItemResponse response = cartItemService.deleteItem(userDetails.getUserId(), cartItemId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<ClearCartResponse>> clearCart(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        ClearCartResponse response = cartService.clearCart(userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
