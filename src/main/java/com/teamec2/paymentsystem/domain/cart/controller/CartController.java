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
    public ResponseEntity<ApiResponse<CartItemCommandResponse>> addCartItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        CartItemCommandResponse response = cartItemService.addCartItem(
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
    public ResponseEntity<ApiResponse<CartItemCommandResponse>> updateCartItemQuantity(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cartItemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        CartItemCommandResponse response = cartItemService.updateCartItemQuantity(
                userDetails.getUserId(),
                cartItemId,
                request.quantity()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<DeleteCartItemResponse>> deleteCartItem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cartItemId
    ) {
        DeleteCartItemResponse response = cartItemService.deleteCartItem(userDetails.getUserId(), cartItemId);

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
