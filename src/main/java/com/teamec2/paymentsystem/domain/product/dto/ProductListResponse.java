package com.teamec2.paymentsystem.domain.product.dto;

import java.time.LocalDateTime;

public record ProductListResponse(
        Long productId,
        String name,
        int price,
        int stock,
        String category,
        String status,
        LocalDateTime createdAt
) {
}
