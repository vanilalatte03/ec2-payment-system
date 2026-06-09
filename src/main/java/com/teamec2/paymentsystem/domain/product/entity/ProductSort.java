package com.teamec2.paymentsystem.domain.product.entity;

import org.springframework.data.domain.Sort;

public enum ProductSort {
    LATEST,
    PRICE_ASC,
    PRICE_DESC;

    public Sort toSort() {
        return switch (this) {
            case LATEST -> Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
            case PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
            case PRICE_DESC -> Sort.by(Sort.Direction.DESC, "price").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }
}
