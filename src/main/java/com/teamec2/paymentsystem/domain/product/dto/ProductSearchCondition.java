package com.teamec2.paymentsystem.domain.product.dto;

import com.teamec2.paymentsystem.domain.product.entity.ProductCategory;
import com.teamec2.paymentsystem.domain.product.entity.ProductStatus;

public record ProductSearchCondition(
        ProductCategory category,
        Integer minPrice,
        Integer maxPrice,
        ProductStatus status
) {
}
