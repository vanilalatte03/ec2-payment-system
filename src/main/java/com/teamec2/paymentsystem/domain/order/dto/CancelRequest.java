package com.teamec2.paymentsystem.domain.order.dto;

import jakarta.validation.Valid;

import java.util.List;

public record CancelRequest(
        List<Long> orderItemIds,
        List<@Valid CancelItemRequest> items
) {
    public static CancelRequest fromOrderItemIds(List<Long> orderItemIds) {
        return new CancelRequest(orderItemIds, null);
    }
}
