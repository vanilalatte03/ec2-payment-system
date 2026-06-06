package com.teamec2.paymentsystem.domain.order.dto;

import jakarta.validation.Valid;

import java.util.List;

public record CancelOrderRequest(
        List<Long> orderItemIds,
        List<@Valid CancelOrderItemRequest> items
) {
    public static CancelOrderRequest fromOrderItemIds(List<Long> orderItemIds) {
        return new CancelOrderRequest(orderItemIds, null);
    }
}
