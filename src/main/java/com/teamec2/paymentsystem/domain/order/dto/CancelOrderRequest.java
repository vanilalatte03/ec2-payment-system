package com.teamec2.paymentsystem.domain.order.dto;

import java.util.List;

public record CancelOrderRequest(
        List<Long> orderItemIds
) {
}
