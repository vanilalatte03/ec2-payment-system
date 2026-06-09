package com.teamec2.paymentsystem.domain.product.dto;

import java.time.LocalDateTime;

public record ProductDetailResponse (
        Long productId,
        String name,
        int price,
        int stock,
        String description,
        String category,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
